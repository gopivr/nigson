package com.nigson.commerce.fulfillment;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.inventory.InventoryAuthority;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.security.WorkloadCredentials;
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="NIGSON_CARRIER_CANCELLATION_ENABLED",havingValue="true")
public class CarrierCancellationConfiguration {
 private void enabled(Environment env){if(!env.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false)||!env.getProperty("NIGSON_FULFILLMENT_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_CANCELLATION_SECURITY_REQUIRED");long delay=env.getProperty("NIGSON_CARRIER_CANCELLATION_DELAY_MS",Long.class,30000L);if(delay<1000||delay>300000)throw new IllegalArgumentException("CARRIER_CANCELLATION_DELAY_INVALID");}
 @Bean CarrierCancellationService carrierCancellationService(JdbcTemplate jdbc,JdbcShipmentRepository shipments,InventoryAuthority authority,PlatformStore commands,AuditWriter audit,FulfillmentPolicy policy,PlatformTransactionManager manager,Environment env){enabled(env);return new CarrierCancellationService(jdbc,shipments,authority,commands,audit,policy,manager,Clock.systemUTC());}
 @Bean CarrierCancellationPort carrierCancellationPort(Environment env,WorkloadCredentials credentials){enabled(env);return new HttpCarrierCancellation(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token);}
 @Bean CarrierCancellationRelay carrierCancellationRelay(JdbcTemplate jdbc,PlatformTransactionManager manager,CarrierCancellationPort remote,CarrierCancellationService source,AuditWriter audit){return new CarrierCancellationRelay(jdbc,manager,remote,source,audit,Clock.systemUTC());}
}
