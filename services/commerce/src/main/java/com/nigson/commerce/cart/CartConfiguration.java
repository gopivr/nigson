package com.nigson.commerce.cart;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.*;
@Configuration(proxyBeanMethods=false)
public class CartConfiguration {
 @Bean CartCheckout cartCheckout(JdbcTemplate jdbc,Pricing pricing,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,@org.springframework.beans.factory.annotation.Value("${NIGSON_PRODUCT_PHASE:1}")int phase){var clock=Clock.systemUTC();return new JdbcCartCheckout(jdbc,new JdbcCartRepository(jdbc,clock,pricing,phase),clock,audit,metrics);}
 @Bean CartRecoveryService cartRecoveryService(JdbcTemplate jdbc,CartCheckout checkout,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,org.springframework.transaction.PlatformTransactionManager manager){return new CartRecoveryService(jdbc,checkout,audit,metrics,manager,Clock.systemUTC());}
 @Bean CartService cartService(JdbcTemplate jdbc,Pricing pricing,PricingAuthority authority,PlatformStore commands,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,org.springframework.transaction.PlatformTransactionManager manager,@org.springframework.beans.factory.annotation.Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new CartService(new JdbcCartRepository(jdbc,Clock.systemUTC(),pricing,phase),authority,jdbc,commands,audit,metrics,manager);}
}
