package org.rrd4j.core;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * @author yangzj
 * @date 2021/12/7
 */
public class RrdDbHelper {

  static final RrdDbPool pool = new RrdDbPool();

  public static RrdDbPool getRrdDbPool(){
    return pool;
  }

  public static RrdDb of(RrdDef rrdDef) throws IOException {
    RrdDb db = null;
    File rrdFile = new File(rrdDef.getPath());
    if (rrdFile.exists()) {
      db = RrdDb.getBuilder().setPool(pool).setPath(rrdFile.toURI())
        .build();
      //LOG.info("check rrd def");
      if(!db.getRrdDef().equals(rrdDef)){
        //LOG.info("rrd def is changed");
        db.close();
        //LOG.info("rrd db close");
        final String oldPath = rrdFile.getPath() + "."+ System.currentTimeMillis();
        Files.move(rrdFile.toPath(), Paths.get(oldPath), StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        //LOG.info("rrd db backup");
        db = RrdDb.getBuilder().setPool(pool).setRrdDef(rrdDef)
          .build();
        //LOG.info("build new rrd db");
        try(RrdDb old = RrdDb.of(oldPath)){
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
      }
    }else {
      db = RrdDb.getBuilder().setPool(pool).setRrdDef(rrdDef)
        .build();
    }
    return db;
  }
}
