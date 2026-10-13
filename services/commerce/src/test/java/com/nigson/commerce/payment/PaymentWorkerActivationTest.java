package com.nigson.commerce.payment;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.order.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual conditional bean registration, including independent scanning of the nested configuration. */
class PaymentWorkerActivationTest {
 private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(PaymentConfiguration.Active.Polling.class);
 @Test void disabledPaymentsDoNotCreateOrRequireAWorker(){context.run(c->assertThat(c).hasNotFailed().doesNotHaveBean(PaymentWorker.class));context.withPropertyValues("NIGSON_PAYMENT_ENABLED=false","NIGSON_PAYMENT_WORKER_ENABLED=true").run(c->assertThat(c).hasNotFailed().doesNotHaveBean(PaymentWorker.class));}
 @Test void explicitlyDisabledPollingDoesNotRequireFinancialAdapters(){context.withPropertyValues("NIGSON_PAYMENT_ENABLED=true","NIGSON_PAYMENT_WORKER_ENABLED=false").run(c->assertThat(c).hasNotFailed().doesNotHaveBean(PaymentWorker.class));}
 @Test void enabledPaymentsRegisterTheActualWorkerWhenItsDependenciesExist(){var jdbc=mock(JdbcTemplate.class);context.withPropertyValues("NIGSON_PAYMENT_ENABLED=true").withBean(JdbcTemplate.class,()->jdbc).withBean(OrderPolicy.class,()->new OrderPolicy(true,"test","MOCK","synthetic-merchant",List.of(UUID.randomUUID()),900)).withBean(PaymentPolicy.class,()->new PaymentPolicy(true,"test",true,"",3600)).withBean(CheckoutPayments.class,()->mock(CheckoutPayments.class)).withBean(RefundOperations.class,()->mock(RefundOperations.class)).withBean(PaymentReceiptProcessor.class,()->mock(PaymentReceiptProcessor.class)).withBean(PaymentExpiry.class,()->mock(PaymentExpiry.class)).run(c->{assertThat(c).hasNotFailed().hasSingleBean(PaymentWorker.class);assertThat(mockingDetails(jdbc).getInvocations().stream().filter(call->!call.getMethod().getName().equals("afterPropertiesSet")).toList()).isEmpty();});}
}
