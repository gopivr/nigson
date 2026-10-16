package com.nigson.commerce.fulfillment;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.commerce.inventory.InventoryAuthority;
import com.nigson.commerce.security.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_CARRIER_RECONCILIATION_ENABLED",havingValue="true")
public class CarrierReconciliationConfiguration {
 @Bean CarrierReconciliationCommands carrierReconciliationCommands(JdbcTemplate jdbc,PlatformTransactionManager manager,InventoryAuthority authority,AuditWriter audit,Environment env){if(!env.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_RECONCILIATION_SECURITY_REQUIRED");return new CarrierReconciliationCommands(jdbc,manager,authority,audit,Clock.systemUTC());}
 @Bean CarrierReconciliationPort carrierReconciliationPort(Environment env,WorkloadCredentials credentials){return new HttpCarrierReconciliation(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token);}
 @Bean CarrierReconciliationService carrierReconciliationService(CarrierReconciliationCommands commands,CarrierReconciliationPort remote,ActorDelegation delegation){return new CarrierReconciliationService(commands,remote,delegation);}
}
