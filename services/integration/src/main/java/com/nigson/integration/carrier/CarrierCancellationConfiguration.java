package com.nigson.integration.carrier;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.integration.provider.WorkloadIdentity;
import com.nigson.integration.providers.CarrierProvider;
import com.nigson.shared.problem.ApiValues;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_CARRIER_CANCELLATION_ENABLED",havingValue="true")
public class CarrierCancellationConfiguration {
 private void enabled(Environment env){for(String key:List.of("NIGSON_SECURITY_ENABLED","NIGSON_CARRIER_ENABLED","NIGSON_CARRIER_WORKER_ENABLED"))if(!env.getProperty(key,Boolean.class,false))throw new IllegalArgumentException("CARRIER_CANCELLATION_PREREQUISITES_REQUIRED");long delay=env.getProperty("NIGSON_CARRIER_CANCELLATION_DELAY_MS",Long.class,30000L);if(delay<1000||delay>300000)throw new IllegalArgumentException("CARRIER_CANCELLATION_DELAY_INVALID");}
 @Bean CarrierCancellationService carrierCancellationService(JdbcTemplate jdbc,PlatformTransactionManager manager,CarrierProvider provider,AuditWriter audit,Environment env){enabled(env);return new CarrierCancellationService(jdbc,manager,provider,audit,Clock.systemUTC(),ApiValues.uuid(env.getRequiredProperty("NIGSON_CARRIER_CREATION_GENERATION")));}
 @Bean CarrierCancellationExecution carrierCancellationExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,CarrierProvider provider,Environment env){enabled(env);return new CarrierCancellationExecution(jdbc,manager,audit,Clock.systemUTC(),ApiValues.uuid(env.getRequiredProperty("NIGSON_CARRIER_CREATION_GENERATION")),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_CREATION_NOT_BEFORE")),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_APPROVAL_UNTIL")),provider.mode()==com.nigson.shared.security.ProviderHttp.Mode.TEST?"SANDBOX":provider.mode().name(),provider.accountFingerprint());}
 @Bean CarrierCancellationResultClient carrierCancellationResultClient(Environment env){enabled(env);var identity=new WorkloadIdentity(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_OIDC_ISSUER"),env.getRequiredProperty("NIGSON_INTEGRATION_WORKLOAD_SECRET"),env.getProperty("NIGSON_PRODUCT_PHASE",Integer.class,1),Clock.systemUTC());return new CarrierCancellationResultClient(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_COMMERCE_BASE_URL"),identity::token);}
 @Bean CarrierCancellationWorker carrierCancellationWorker(CarrierCancellationExecution execution,CarrierProvider provider,CarrierCancellationResultClient source){return new CarrierCancellationWorker(execution,provider,source,Clock.systemUTC());}
}
