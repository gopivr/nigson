package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.integration.provider.WorkloadIdentity;
import com.nigson.integration.providers.CarrierProvider;
import com.nigson.shared.problem.ApiValues;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_CARRIER_WORKER_ENABLED",havingValue="true")
@EnableScheduling
public class CarrierWorkerConfiguration {
 private void enabled(Environment env){for(String key:List.of("NIGSON_SECURITY_ENABLED","NIGSON_CARRIER_ENABLED","NIGSON_CARRIER_BOOKING_INTAKE_ENABLED","NIGSON_CARRIER_LABELS_ENABLED"))if(!env.getProperty(key,Boolean.class,false))throw new IllegalArgumentException("CARRIER_WORKER_PREREQUISITES_REQUIRED");long delay=env.getProperty("NIGSON_CARRIER_WORKER_DELAY_MS",Long.class,30000L);if(delay<1000||delay>300000)throw new IllegalArgumentException("CARRIER_WORKER_DELAY_INVALID");}
 @Bean CarrierBookingExecution carrierBookingExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,CarrierProvider provider,Environment env,MeterRegistry metrics){enabled(env);return new CarrierBookingExecution(jdbc,manager,audit,Clock.systemUTC(),provider.mode(),provider.accountFingerprint(),ApiValues.uuid(env.getRequiredProperty("NIGSON_CARRIER_CREATION_GENERATION")),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_CREATION_NOT_BEFORE")),metrics);}
 @Bean CarrierContextClient carrierContextClient(Environment env){enabled(env);var identity=new WorkloadIdentity(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_OIDC_ISSUER"),env.getRequiredProperty("NIGSON_INTEGRATION_WORKLOAD_SECRET"),env.getProperty("NIGSON_PRODUCT_PHASE",Integer.class,1),Clock.systemUTC());return new CarrierContextClient(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_COMMERCE_BASE_URL"),identity::token);}
 @Bean CarrierShipmentRequest carrierShipmentRequest(Environment env){enabled(env);return new CarrierShipmentRequest(Set.of(env.getRequiredProperty("NIGSON_CARRIER_SERVICES").split(",",-1)),env.getRequiredProperty("NIGSON_CARRIER_PICKUP_TYPE"),env.getRequiredProperty("NIGSON_CARRIER_LABEL_STOCK"),env.getRequiredProperty("NIGSON_CARRIER_BILLING_TYPE"),env.getRequiredProperty("NIGSON_CARRIER_APPROVAL"),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_APPROVAL_UNTIL")),Clock.systemUTC());}
 @Bean CarrierBookingWorker carrierBookingWorker(CarrierBookingExecution execution,CarrierContextClient source,CarrierShipmentRequest requests,CarrierProvider provider,CarrierLabelStorage labels,Environment env){enabled(env);return new CarrierBookingWorker(execution,source,requests,provider,labels,Clock.systemUTC(),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_APPROVAL_UNTIL")));}
 @Bean CarrierTrackingExecution carrierTrackingExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,CarrierProvider provider,Environment env,MeterRegistry metrics){enabled(env);return new CarrierTrackingExecution(jdbc,manager,audit,Clock.systemUTC(),provider.mode(),provider.accountFingerprint(),metrics);}
 @Bean CarrierTrackingWorker carrierTrackingWorker(CarrierTrackingExecution execution,CarrierProvider provider,CarrierContextClient source){return new CarrierTrackingWorker(execution,provider,source,Clock.systemUTC());}
}
