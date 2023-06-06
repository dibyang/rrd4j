package org.rrd4j.core;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * RRD数据结构自动升级和RrdDb池支持
 * @author yangzj
 * @date 2021/12/7
 */
public class RrdDbHelper {
  static final Logger LOG = LoggerFactory.getLogger(RrdDbHelper.class);

  static final RrdDbPool pool = new RrdDbPool();

  /**
   * 获取默认的RrdDb池
   * @return 默认的RrdDb池
   */
  public static RrdDbPool getRrdDbPool(){
    return pool;
  }

  /**
   * 根据RrdDef的定义，确定是否需要升级RRD数据结构
   * @param rrdDef
   * @return
   * @throws IOException
   */
  public static RrdDb of(RrdDef rrdDef) throws IOException {
    RrdDb db = null;

    File rrdFile = new File(rrdDef.getPath());
    if (rrdFile.exists()) {
      db = RrdDb.getBuilder().setPool(pool).setPath(rrdFile.toURI())
        .build();
      //LOG.info("check rrd def");
      final String oldPath = rrdFile.getPath() + ".old";
      final String bakPath = rrdFile.getPath() + ".bak";
      File oldFile = new File(oldPath);
      if(!db.getRrdDef().equals(rrdDef)){
        LOG.info("rrd {} def need upgrade.", rrdDef.getPath());
        db.close();
        //LOG.info("rrd db close");

        Files.move(rrdFile.toPath(), Paths.get(oldPath), StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        //LOG.info("rrd db backup");
        db = RrdDb.getBuilder().setPool(pool).setRrdDef(rrdDef)
          .build();
        //LOG.info("build new rrd db");
        importOldData(db, oldFile, bakPath);
        LOG.info("rrd {} upgrade finished.", rrdDef.getPath());
      }else if(oldFile.exists()){
        importOldData(db, oldFile, bakPath);
        LOG.info("rrd {} upgrade finished.", rrdDef.getPath());
      }
    }else {
      db = RrdDb.getBuilder().setPool(pool).setRrdDef(rrdDef)
        .build();
    }
    return db;
  }

  private static void importOldData(RrdDb db, File oldFile, String bakPath) throws IOException {
    try(RrdDb old = RrdDb.of(oldFile.getPath())){
      final Datasource[] datasources = db.getDatasources();
      for (int i = 0; i < datasources.length; i++) {
        Datasource ds = datasources[i];
        if (old.containsDs(ds.getName())) {
          Datasource oldDatasource = old.getDatasource(ds.getName());
          if(oldDatasource.getType().equals(ds.getType())){
            oldDatasource.copyStateTo(ds);
            //LOG.info("update rrd ds: {}", ds.getName());
          }
        }
      }
      Archive[] archives = db.getArchives();
      for (int i = 0; i < archives.length; i++) {
        Archive archive = archives[i];
        final Archive oldArchive = old.getArchive(archive.getConsolFun(), archive.getSteps());
        if(oldArchive!=null){
          oldArchive.copyStateTo(archive);
          //LOG.info("update rrd archive: {} {}", oldArchive.getConsolFun(), oldArchive.getArcStep());
        }
      }
    }
    Files.move(oldFile.toPath(), Paths.get(bakPath), StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }
}
