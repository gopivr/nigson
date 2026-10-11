package com.nigson.commerce.payment;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.order.*;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
@Configuration(proxyBeanMethods=false)
public class CheckoutPaymentConfiguration {
 @Bean CheckoutPayments checkoutPayments(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectProvider<PaystackAdapter> adapters,OrderPolicy policy,OrderLifecycle orders,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,@Value("${PAYSTACK_MODE:}")String mode){var adapter=adapters.getIfAvailable();if(policy.enabled()&&(adapter==null||!mode.equals(policy.mode())))throw new IllegalArgumentException("ORDER_PAYMENT_ADAPTER_REQUIRED");return new CheckoutPaymentCoordinator(jdbc,manager,adapter,policy,orders,audit,Clock.systemUTC(),metrics);}
}
