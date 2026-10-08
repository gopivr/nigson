package com.nigson.experience.platform;
import io.micrometer.core.instrument.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
/** Actual bounded owner snapshots; unavailable data is NaN, never fabricated healthy zero. */
public final class PlatformMetrics implements AutoCloseable {
 private record Snapshot(double pending,double age){}
 private final AtomicReference<Snapshot> snapshot=new AtomicReference<>();
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"owner-metrics");thread.setDaemon(true);return thread;});
 public PlatformMetrics(JdbcTemplate jdbc,javax.sql.DataSource source,MeterRegistry registry){
  var query=new JdbcTemplate(jdbc.getDataSource());query.setQueryTimeout(2);
  Gauge.builder("nigson.outbox.pending",snapshot,v->v.get()==null?Double.NaN:v.get().pending()).tag("owner","experience").register(registry);
  Gauge.builder("nigson.outbox.oldest.age",snapshot,v->v.get()==null?Double.NaN:v.get().age()).tag("owner","experience").register(registry);
  try {HikariDataSource pool=source.unwrap(HikariDataSource.class);
   Gauge.builder("nigson.db.pool.active",pool,p->p.getHikariPoolMXBean()==null?Double.NaN:p.getHikariPoolMXBean().getActiveConnections()).tag("owner","experience").register(registry);
   Gauge.builder("nigson.db.pool.utilization",pool,p->p.getHikariPoolMXBean()==null?Double.NaN:p.getHikariPoolMXBean().getActiveConnections()/(double)p.getMaximumPoolSize()).tag("owner","experience").register(registry);
   Gauge.builder("nigson.db.pool.waiting",pool,p->p.getHikariPoolMXBean()==null?Double.NaN:p.getHikariPoolMXBean().getThreadsAwaitingConnection()).tag("owner","experience").register(registry);
  }catch(java.sql.SQLException unsupported){throw new IllegalStateException("POOL_OBSERVATION_UNAVAILABLE");}
  executor.scheduleWithFixedDelay(()->{
   try {snapshot.set(query.queryForObject("SELECT count(*),greatest(0,coalesce(extract(epoch FROM(now()-min(created_at))),0)) FROM experience.outbox_event WHERE state IN ('PENDING','CLAIMED')",(row,index)->new Snapshot(row.getDouble(1),row.getDouble(2))));}
   catch(org.springframework.dao.DataAccessException unavailable){snapshot.set(null);}
  },0,10,TimeUnit.SECONDS);
 }
 public void close(){executor.shutdownNow();}
}
