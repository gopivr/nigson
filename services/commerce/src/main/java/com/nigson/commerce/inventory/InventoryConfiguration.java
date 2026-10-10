package com.nigson.commerce.inventory;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
public class InventoryConfiguration {
 @Bean InventoryAuthority inventoryAuthority(JdbcTemplate jdbc){return new InventoryAuthority(jdbc);}
 @Bean InventoryService inventoryService(JdbcTemplate jdbc,InventoryAuthority authority,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics){var clock=Clock.systemUTC();return new InventoryService(new JdbcInventoryRepository(jdbc,clock),authority,commands,audit,clock,manager,jdbc,metrics);}
 @Bean StockLedger stockLedger(JdbcTemplate jdbc,InventoryService events,AuditWriter audit){return new JdbcStockLedger(jdbc,events,audit,Clock.systemUTC());}
}
