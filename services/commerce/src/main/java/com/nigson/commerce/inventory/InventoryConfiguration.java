package com.nigson.commerce.inventory;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
public class InventoryConfiguration {
 @Bean StockAvailability stockAvailability(JdbcTemplate jdbc,@org.springframework.beans.factory.annotation.Value("${NIGSON_ORDER_STOCK_LOCATION_PRIORITY:}")String locations,@org.springframework.beans.factory.annotation.Value("${NIGSON_LOW_STOCK_THRESHOLD:10}")java.math.BigDecimal low){var ids=locations.isBlank()?java.util.List.<java.util.UUID>of():java.util.Arrays.stream(locations.split(",",-1)).map(com.nigson.shared.problem.ApiValues::uuid).toList();return new StockAvailability(jdbc,ids,low);}
 @Bean InventoryAuthority inventoryAuthority(JdbcTemplate jdbc){return new InventoryAuthority(jdbc);}
 @Bean InventoryService inventoryService(JdbcTemplate jdbc,InventoryAuthority authority,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics){var clock=Clock.systemUTC();return new InventoryService(new JdbcInventoryRepository(jdbc,clock),authority,commands,audit,clock,manager,jdbc,metrics);}
 @Bean StockLedger stockLedger(JdbcTemplate jdbc,InventoryService events,AuditWriter audit){return new JdbcStockLedger(jdbc,events,audit,Clock.systemUTC());}
}
