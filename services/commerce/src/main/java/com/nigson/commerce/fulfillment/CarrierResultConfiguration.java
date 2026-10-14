package com.nigson.commerce.fulfillment;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.files.FileStorage;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_CARRIER_RESULTS_ENABLED",havingValue="true")
public class CarrierResultConfiguration {
 @Bean CarrierResultService carrierResultService(Environment environment,JdbcTemplate jdbc,JdbcShipmentRepository repository,FileStorage storage,FulfillmentPolicy policy,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics){if(!"true".equals(environment.getProperty("NIGSON_SECURITY_ENABLED")))throw new IllegalArgumentException("CARRIER_PRIVATE_SECURITY_REQUIRED");return new CarrierResultService(jdbc,repository,storage,policy,commands,audit,manager,java.time.Clock.systemUTC(),environment.getRequiredProperty("APP_ENV"),metrics);}
}
