package com.nigson.integration.platform;
import com.nigson.shared.observability.WireContext;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import io.micrometer.core.instrument.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import java.util.function.Supplier;
/** JDBC protocol instrumentation without SQL text, bindings or exception events. */
public final class JdbcTrace extends DelegatingDataSource {
 private final Supplier<MeterRegistry> metrics;
 public JdbcTrace(DataSource source,Supplier<MeterRegistry> metrics){super(source);this.metrics=metrics;}
 public Connection getConnection()throws SQLException{try{return wrapConnection(super.getConnection());}catch(SQLTransientConnectionException failure){metrics.get().counter("nigson.db.pool.timeouts","owner","integration").increment();throw failure;}}
 public Connection getConnection(String user,String password)throws SQLException{try{return wrapConnection(super.getConnection(user,password));}catch(SQLTransientConnectionException failure){metrics.get().counter("nigson.db.pool.timeouts","owner","integration").increment();throw failure;}}
 private Connection wrapConnection(Connection connection){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
  Object result=invoke(connection,method,args);
  if(result instanceof Statement statement){String operation=args!=null&&args.length>0&&args[0] instanceof String sql?operation(sql):"OTHER";Class<?> iface=result instanceof CallableStatement?CallableStatement.class:result instanceof PreparedStatement?PreparedStatement.class:Statement.class;return Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(p,m,a)->{
   if(!m.getName().startsWith("execute"))return invoke(statement,m,a);var wire=WireContext.current();if(wire==null)return invoke(statement,m,a);
   String label=a!=null&&a.length>0&&a[0] instanceof String sql?operation(sql):operation;var span=wire.tracer.spanBuilder("db.execute").setSpanKind(SpanKind.CLIENT).setAttribute("db.system.name","postgresql").setAttribute("db.operation.name",label).startSpan();long started=System.nanoTime();
   try(Scope scope=span.makeCurrent()){return invoke(statement,m,a);}catch(Throwable failure){span.setStatus(StatusCode.ERROR);throw failure;}finally{span.end();Timer.builder("nigson.db.query").tags("owner","integration","operation",label).register(metrics.get()).record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);}
  });}return result;
 });}
 private static String operation(String sql){String first=sql.stripLeading().split("\\s+",2)[0].toUpperCase(java.util.Locale.ROOT);return java.util.Set.of("SELECT","INSERT","UPDATE","DELETE").contains(first)?first:"OTHER";}
 private static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
}
