package com.nigson.integration.provider;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.integration.platform.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods=false)
public class JobConfiguration {
 @Bean JobRepository integrationJobs(JdbcTemplate jdbc){return new JdbcJobRepository(jdbc,Clock.systemUTC());}
 @Bean JobService integrationJobService(JobRepository jobs,PlatformStore store,AuditWriter audit,PlatformTransactionManager manager){return new JobService(jobs,store,audit,manager,Clock.systemUTC());}
 @Bean JobExecution integrationJobExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,JobRepository jobs,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics){return new JobExecution(jdbc,manager,jobs,audit,Clock.systemUTC(),metrics);}
 @Configuration(proxyBeanMethods=false)
 @ConditionalOnProperty(name="NIGSON_PROVIDER_JOBS_ENABLED",havingValue="true")
 public static class Active {
  @Bean WorkloadIdentity integrationWorkerIdentity(Environment env){return new WorkloadIdentity(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_OIDC_ISSUER"),env.getRequiredProperty("NIGSON_INTEGRATION_WORKLOAD_SECRET"),env.getProperty("NIGSON_PRODUCT_PHASE",Integer.class,1),Clock.systemUTC());}
  @Bean PaymentReconciliationHandler paymentReconciliationHandler(Environment env,WorkloadIdentity identity){return new PaymentReconciliationHandler(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_COMMERCE_BASE_URL"),env.getProperty("NIGSON_PAYMENT_RECONCILIATION_LIMIT",Integer.class,25),identity::token);}
  @Bean OrderPendingHandler orderPendingHandler(JobRepository jobs,AuditWriter audit){return new OrderPendingHandler(jobs,audit);}
  @Bean JobWorker jobWorker(JobExecution execution,java.util.List<JobHandler> handlers,Environment env){long delay=env.getProperty("NIGSON_PROVIDER_WORKER_DELAY_MS",Long.class,30000L);if(delay<1000||delay>300000)throw new IllegalArgumentException("JOB_WORKER_DELAY_INVALID");return new JobWorker(execution,handlers,env.getProperty("NIGSON_PROVIDER_WORKER_BATCH",Integer.class,25));}
  @Configuration(proxyBeanMethods=false)
  @ConditionalOnProperty(name="NIGSON_PROVIDER_WORKER_ENABLED",havingValue="true",matchIfMissing=true)
  @EnableScheduling public static class Polling {}
 }
}
