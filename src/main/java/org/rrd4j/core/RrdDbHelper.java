package org.rrd4j.core;


import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    synchronized (rrdDef.getPath().intern()){
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
    }
    return db;
  }

  private static void importOldData(RrdDb db, File oldFile, String bakPath) throws IOException {
    if (oldFile.exists()) {
      try (RrdDb old = RrdDb.of(oldFile.getPath())) {
        final Datasource[] datasources = db.getDatasources();
        for (int i = 0; i < datasources.length; i++) {
          Datasource ds = datasources[i];
          if (old.containsDs(ds.getName())) {
            Datasource oldDatasource = old.getDatasource(ds.getName());
            if (oldDatasource.getType().equals(ds.getType())) {
              oldDatasource.copyStateTo(ds);
              //LOG.info("update rrd ds: {}", ds.getName());
            }
          }
        }
        Archive[] archives = db.getArchives();
        for (int i = 0; i < archives.length; i++) {
          Archive archive = archives[i];
          final Archive oldArchive = old.getArchive(archive.getConsolFun(), archive.getSteps());
          if (oldArchive != null) {
            oldArchive.copyStateTo(archive);
            //LOG.info("update rrd archive: {} {}", oldArchive.getConsolFun(), oldArchive.getArcStep());
          }
        }
      }
      Files.move(oldFile.toPath(), Paths.get(bakPath), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    ExecutorService service = Executors.newFixedThreadPool(50);
    int count =300;
    int n = 5;
    CountDownLatch countDownLatch = new CountDownLatch(n*count);
    for (int i = 0; i < count; i++) {
      int finalI = i;
      RrdDef rrdDef = buildRrdDef("/test/rrd/test_"+i+".rrd");
      for (int j = 0; j < n; j++) {
        service.submit(()->{
          try(RrdDb rrdDb = RrdDbHelper.of(rrdDef)) {
            //do something
            Thread.sleep(100);
            int fileCount = RrdDbHelper.getRrdDbPool().getOpenFileCount();
            System.out.println(finalI + " fileCount = " + fileCount);
          } catch (Exception e) {
            e.printStackTrace();
          }
          countDownLatch.countDown();
        });
      }
    }
    countDownLatch.await();
    int fileCount = RrdDbHelper.getRrdDbPool().getOpenFileCount();
    System.out.println("fileCount = " + fileCount);
    service.shutdown();
  }

  private static RrdDef buildRrdDef(String rrdPath) {
    File rrddir = new File(rrdPath).getParentFile();
    if (!rrddir.exists()) {
      rrddir.mkdirs();
    }
    RrdDef rrdDef;
    rrdDef = new RrdDef(rrdPath, 5);
    for (String type  : Arrays.asList("cpu_user","cpu_wait","cpu_sys"
        //,"men_free","men_used"
    )) {
      rrdDef.addDatasource(type, DsType.GAUGE, 60, 0, Double.MAX_VALUE);
    }
    rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 1, 240);
    rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 6, 240);
    rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 60, 288);
    rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 720, 240);
    rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 2880, 240);
    rrdDef.addArchive(ConsolFun.AVERAGE, 0.5, 17280, 1000);
    rrdDef.addArchive(ConsolFun.MAX, 0.5, 1, 240);
    rrdDef.addArchive(ConsolFun.MAX, 0.5, 6, 240);
    rrdDef.addArchive(ConsolFun.MAX, 0.5, 60, 288);
    rrdDef.addArchive(ConsolFun.MAX, 0.5, 720, 240);
    rrdDef.addArchive(ConsolFun.MAX, 0.5, 2880, 240);
    rrdDef.addArchive(ConsolFun.MAX, 0.5, 17280, 1000);
    rrdDef.addArchive(ConsolFun.MIN, 0.5, 1, 240);
    rrdDef.addArchive(ConsolFun.MIN, 0.5, 6, 240);
    rrdDef.addArchive(ConsolFun.MIN, 0.5, 60, 288);
    rrdDef.addArchive(ConsolFun.MIN, 0.5, 720, 240);
    rrdDef.addArchive(ConsolFun.MIN, 0.5, 2880, 240);
    rrdDef.addArchive(ConsolFun.MIN, 0.5, 17280, 1000);

    return rrdDef;
  }
}
