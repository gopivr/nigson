package com.nigson.integration.carrier;

import com.nigson.integration.providers.PandagoAdapter;
import com.nigson.integration.platform.PlatformStore;
import com.nigson.integration.platform.AuditWriter;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

/** Carrier orchestration depends on provider adapters, never the reverse. */
@Configuration(proxyBeanMethods=false)
public class PandagoConfiguration {
 @Bean @ConditionalOnProperty(name="NIGSON_PANDAGO_ENABLED",havingValue="true")
 PandagoQuoteService pandagoQuoteService(PandagoAdapter provider,PlatformStore store,AuditWriter audit,Environment e,com.nigson.integration.providers.GeocodingProvider geocoder){
  return new PandagoQuoteService(provider,store,audit,java.time.Clock.systemUTC(),e.getRequiredProperty("NIGSON_PANDAGO_APPROVAL"),java.time.Instant.parse(e.getRequiredProperty("NIGSON_PANDAGO_APPROVAL_UNTIL")),geocoder);
 }
 @Configuration(proxyBeanMethods=false)
 @org.springframework.scheduling.annotation.EnableScheduling
 @ConditionalOnProperty(name="NIGSON_PANDAGO_BOOKING_ENABLED",havingValue="true")
 public static class Booking {
  @Bean PandagoBookingRequested pandagoBookingRequested(org.springframework.core.env.Environment e){return new PandagoBookingRequested(java.util.UUID.fromString(e.getRequiredProperty("NIGSON_CARRIER_CREATION_GENERATION")),java.time.Instant.parse(e.getRequiredProperty("NIGSON_CARRIER_CREATION_NOT_BEFORE")),java.time.Clock.systemUTC(),"TEST".equals(e.getRequiredProperty("PANDAGO_MODE"))?"SANDBOX":e.getRequiredProperty("PANDAGO_MODE"));}
  @Bean PandagoCommerceClient pandagoCommerceClient(com.nigson.integration.provider.WorkloadIdentity identity,org.springframework.core.env.Environment e){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_PRIVATE_SECURITY_REQUIRED");return new PandagoCommerceClient(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_COMMERCE_BASE_URL"),identity::token);}
  @Bean PandagoBookingWorker pandagoBookingWorker(org.springframework.jdbc.core.JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager,PandagoAdapter provider,com.nigson.integration.providers.GeocodingProvider geocoder,PandagoCommerceClient commerce,org.springframework.core.env.Environment e,io.micrometer.core.instrument.MeterRegistry metrics){return new PandagoBookingWorker(jdbc,manager,provider,geocoder,commerce,java.time.Clock.systemUTC(),java.util.UUID.fromString(e.getRequiredProperty("NIGSON_CARRIER_CREATION_GENERATION")),java.time.Instant.parse(e.getRequiredProperty("NIGSON_CARRIER_CREATION_NOT_BEFORE")),java.time.Instant.parse(e.getRequiredProperty("NIGSON_PANDAGO_APPROVAL_UNTIL")),metrics);}
 }
}
