package com.nigson.commerce.order;
import java.util.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.*;
import com.nigson.commerce.cart.CartCheckout;
import com.nigson.commerce.inventory.StockLedger;
@Configuration(proxyBeanMethods=false)
public class OrderConfiguration {
 @Bean OrderPolicy orderPolicy(@Value("${NIGSON_ORDER_CHECKOUT_ENABLED:false}")boolean enabled,@Value("${APP_ENV:local}")String environment,@Value("${NIGSON_ORDER_PAYMENT_MODE:}")String mode,@Value("${NIGSON_ORDER_MERCHANT_ACCOUNT:}")String account,@Value("${NIGSON_ORDER_STOCK_LOCATION_PRIORITY:}")String locations,@Value("${NIGSON_ORDER_RESERVATION_SECONDS:900}")int ttl){return new OrderPolicy(enabled,environment,mode,account,locations.isEmpty()?List.of():Arrays.stream(locations.split(",",-1)).map(com.nigson.shared.problem.ApiValues::uuid).toList(),ttl);}
 @Bean OrderAuthority orderAuthority(JdbcTemplate jdbc,PricingAuthority pricing){return new OrderAuthority(jdbc,pricing,Clock.systemUTC());}
 @Bean OrderRepository orderRepository(JdbcTemplate jdbc,CheckoutPricing pricing,CheckoutQuoteRepository quotes,StockLedger stock,CartCheckout carts,OrderPolicy policy){return new JdbcOrderRepository(jdbc,pricing,quotes,stock,carts,policy,Clock.systemUTC());}
 @Bean OrderLifecycle orderLifecycle(JdbcTemplate jdbc,OrderRepository repository,StockLedger stock,CartCheckout carts,AuditWriter audit,PlatformStore events,com.nigson.commerce.reward.RewardOperations rewards,io.micrometer.core.instrument.MeterRegistry metrics,OrderPolicy policy){return new JdbcOrderLifecycle(jdbc,repository,stock,carts,audit,events,rewards,Clock.systemUTC(),metrics,policy.reservationSeconds());}
 @Bean OrderService orderService(OrderRepository repository,OrderAuthority authority,CheckoutPayments payments,PlatformStore commands,AuditWriter audit,JdbcTemplate jdbc,PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics){return new OrderService(repository,authority,payments,commands,audit,jdbc,manager,Clock.systemUTC(),metrics);}
}
