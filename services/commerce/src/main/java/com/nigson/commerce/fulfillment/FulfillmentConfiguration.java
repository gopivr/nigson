package com.nigson.commerce.fulfillment;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.inventory.*;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
@org.springframework.scheduling.annotation.EnableScheduling
public class FulfillmentConfiguration {
 @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="NIGSON_PANDAGO_BOOKING_ENABLED",havingValue="true")
 AutomaticShipmentWorker automaticShipmentWorker(JdbcTemplate jdbc,JdbcShipmentRepository repository,PlatformStore events,AuditWriter audit,org.springframework.transaction.PlatformTransactionManager manager,org.springframework.core.env.Environment env){if(!env.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_PRIVATE_SECURITY_REQUIRED");return new AutomaticShipmentWorker(jdbc,repository,events,audit,manager,Clock.systemUTC(),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_CREATION_NOT_BEFORE")));}

 @Bean JdbcDeliveryRules deliveryRules(JdbcTemplate jdbc){return new JdbcDeliveryRules(jdbc,Clock.systemUTC());}
 @Bean DeliveryRuleService deliveryRuleService(JdbcDeliveryRules rules,InventoryAuthority authority,JdbcTemplate jdbc,PlatformStore commands,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics){return new DeliveryRuleService(rules,authority,jdbc,commands,audit,metrics);}
 @Bean FulfillmentPolicy fulfillmentPolicy(@Value("${NIGSON_FULFILLMENT_ENABLED:false}") boolean enabled,@Value("${APP_ENV:local}") String environment,@Value("${NIGSON_FULFILLMENT_MODE:}") String mode,@Value("${NIGSON_FULFILLMENT_APPROVAL:}") String approval,@Value("${NIGSON_FULFILLMENT_APPROVAL_UNTIL:}") String until,@Value("${NIGSON_FULFILLMENT_MANUAL_CONTINGENCY:}") String manual,@Value("${NIGSON_FULFILLMENT_TRACKING_HOSTS:}") String tracking,@Value("${NIGSON_CARRIER_ACCOUNT_FINGERPRINT:}")String accountHash){return new FulfillmentPolicy(enabled,environment,mode,approval,until.isEmpty()?null:Instant.parse(until),manual,tracking.isEmpty()?Set.of():Set.of(tracking.split(",",-1)),Clock.systemUTC(),accountHash);}
 @Bean ShipmentTrackingService shipmentTrackingService(JdbcTemplate jdbc,ShipmentRepository repository,com.nigson.commerce.order.OrderAuthority authority,org.springframework.transaction.PlatformTransactionManager manager){return new ShipmentTrackingService(jdbc,repository,authority,manager);}
 @Bean CarrierContextService carrierContextService(JdbcShipmentRepository repository,JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager){return new CarrierContextService(repository,jdbc,manager);}
 @Bean JdbcShipmentRepository shipmentRepository(JdbcTemplate jdbc,FulfillmentPolicy policy){return new JdbcShipmentRepository(jdbc,Clock.systemUTC(),policy);}
 @Bean ShipmentService shipmentService(ShipmentRepository repository,StockLedger stock,InventoryAuthority authority,PlatformStore commands,AuditWriter audit,JdbcTemplate jdbc,io.micrometer.core.instrument.MeterRegistry metrics){return new ShipmentService(repository,stock,authority,commands,audit,jdbc,Clock.systemUTC(),metrics);}
}
