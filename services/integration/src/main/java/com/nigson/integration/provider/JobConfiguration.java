package com.nigson.integration.provider;
import java.time.Clock;import java.util.Set;
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
  @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperties({@ConditionalOnProperty(name="NIGSON_CAREER_SHEET_RECIPIENT_GROUP"),@ConditionalOnProperty(name="NIGSON_SHEETS_MODE")}) CareerApplicationCreatedHandler careerApplicationCreatedHandler(JobRepository jobs,AuditWriter audit,Environment env){return new CareerApplicationCreatedHandler(jobs,audit,env.getRequiredProperty("NIGSON_CAREER_SHEET_RECIPIENT_GROUP"));}
  @Bean SheetSyncRepository sheetSyncRepository(JdbcTemplate jdbc,PlatformTransactionManager manager){return new SheetSyncRepository(jdbc,manager,Clock.systemUTC());}
  @Bean @ConditionalOnProperty(name="NIGSON_SHEETS_MODE",havingValue="mock") StatefulGoogleSheetsMock localGoogleSheetsMock(Environment env){if(!Set.of("local","test").contains(env.getRequiredProperty("APP_ENV")))throw new IllegalArgumentException("SHEETS_MOCK_LOCAL_ONLY");return new StatefulGoogleSheetsMock();}
  @Bean @ConditionalOnProperty(name="NIGSON_SHEETS_MODE") CareerSheetContextClient careerSheetContextClient(Environment env,WorkloadIdentity identity){return new CareerSheetContextClient(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_EXPERIENCE_BASE_URL"),identity::token);}
  @Bean @ConditionalOnProperty(name="NIGSON_SHEETS_MODE",havingValue="mock") CareerSheetSyncHandler careerSheetSyncHandler(CareerSheetContextClient context,SheetSyncRepository records,StatefulGoogleSheetsMock sheets,Environment env){String key=env.getRequiredProperty("NIGSON_CAREER_SHEETS_LOCAL_ROW_HASH_KEY");byte[] decoded;try{decoded=java.util.Base64.getDecoder().decode(key);}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("SHEETS_LOCAL_HASH_KEY_INVALID");}return new CareerSheetSyncHandler(context,records,sheets,env.getRequiredProperty("NIGSON_CAREER_SHEET_SPREADSHEET_KEY"),decoded);}
  @Bean @ConditionalOnProperty(name="NIGSON_SHEETS_MODE",havingValue="live") GoogleServiceAccountTokenProvider googleSheetsTokenProvider(Environment env){if(!"prod".equals(env.getRequiredProperty("APP_ENV")))throw new IllegalArgumentException("LIVE_SHEETS_PRODUCTION_ONLY");return new GoogleServiceAccountTokenProvider("prod","https://oauth2.googleapis.com/token",env.getRequiredProperty("NIGSON_GOOGLE_SERVICE_ACCOUNT_EMAIL"),env.getRequiredProperty("NIGSON_GOOGLE_PRIVATE_KEY_PKCS8_BASE64"),Clock.systemUTC());}
  @Bean @ConditionalOnProperty(name="NIGSON_SHEETS_MODE",havingValue="live") GoogleSheetsHttpAdapter googleSheetsHttpAdapter(Environment env,GoogleServiceAccountTokenProvider tokens){return new GoogleSheetsHttpAdapter("prod","https://sheets.googleapis.com",env.getRequiredProperty("NIGSON_CAREER_SHEET_TAB"),tokens);}
  @Bean @ConditionalOnProperty(name="NIGSON_SHEETS_MODE",havingValue="live") CareerSheetSyncHandler liveCareerSheetSyncHandler(CareerSheetContextClient context,SheetSyncRepository records,GoogleSheetsHttpAdapter sheets,Environment env){byte[] key;try{key=java.util.Base64.getDecoder().decode(env.getRequiredProperty("NIGSON_CAREER_SHEETS_ROW_HASH_KEY_BASE64"));}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("SHEETS_ROW_HASH_KEY_INVALID");}return new CareerSheetSyncHandler(context,records,sheets,env.getRequiredProperty("NIGSON_CAREER_SHEET_SPREADSHEET_KEY"),key);}
  @Bean JobWorker jobWorker(JobExecution execution,java.util.List<JobHandler> handlers,Environment env){long delay=env.getProperty("NIGSON_PROVIDER_WORKER_DELAY_MS",Long.class,30000L);if(delay<1000||delay>300000)throw new IllegalArgumentException("JOB_WORKER_DELAY_INVALID");return new JobWorker(execution,handlers,env.getProperty("NIGSON_PROVIDER_WORKER_BATCH",Integer.class,25));}
  @Configuration(proxyBeanMethods=false)
  @ConditionalOnProperty(name="NIGSON_PROVIDER_WORKER_ENABLED",havingValue="true",matchIfMissing=true)
  @EnableScheduling public static class Polling {}
 }
}
