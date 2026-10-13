package com.nigson.commerce.payment;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.order.*;
import com.nigson.commerce.pricing.PricingAuthority;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration(proxyBeanMethods=false)
public class PaymentConfiguration {
 @Bean PaymentPolicy paymentPolicy(@Value("${NIGSON_PAYMENT_ENABLED:false}")boolean enabled,@Value("${APP_ENV:local}")String environment,@Value("${NIGSON_REFUND_TWO_PERSON:true}")boolean twoPerson,@Value("${NIGSON_REFUND_APPROVAL_POLICY_REFERENCE:}")String approval,@Value("${NIGSON_PAYMENT_UNCERTAIN_GRACE_SECONDS:3600}")int grace){return new PaymentPolicy(enabled,environment,twoPerson,approval,grace);}
 @Bean PaymentRepository paymentRepository(JdbcTemplate jdbc,OrderPolicy binding){return new JdbcPaymentRepository(jdbc,Clock.systemUTC(),binding);}
 @Bean PaymentService paymentService(PaymentRepository repository,OrderAuthority orders,PricingAuthority finance,PaymentPolicy policy,PlatformStore commands,AuditWriter audit,JdbcTemplate jdbc,PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics){return new PaymentService(repository,orders,finance,policy,commands,audit,jdbc,manager,Clock.systemUTC(),metrics);}
 @Configuration(proxyBeanMethods=false)
 @ConditionalOnProperty(name="NIGSON_PAYMENT_ENABLED",havingValue="true")
 static class Active {
  @Bean PaystackWebhookProtocol paystackWebhookProtocol(PaystackAdapter provider,OrderPolicy binding,@Value("${PAYSTACK_MODE:}")String mode){if(!binding.enabled()||!mode.equals(binding.mode()))throw new IllegalArgumentException("PAYMENT_PROVIDER_BINDING_REQUIRED");return new PaystackWebhookProtocol(provider::authenticWebhook,provider::webhookDigest,binding.mode());}
  @Bean PaymentPayloadCipher paymentPayloadCipher(ObjectProvider<KmsClient> kms,PaymentPolicy policy,@Value("${NIGSON_PAYMENT_RECEIPT_KEY_REVISION:}")String revision,@Value("${NIGSON_PAYMENT_RECEIPT_KMS_KEY:}")String arn,@Value("${NIGSON_PAYMENT_RECEIPT_WRAPPED_KEY:}")String wrapped,@Value("${NIGSON_PAYMENT_RECEIPT_LOCAL_KEY:}")String local,@Value("${NIGSON_PAYMENT_RECEIPT_PREVIOUS_WRAPPED_KEYS:{}}")String previous){return PaymentReceiptKeys.load(kms.getIfAvailable(),policy.environment(),revision,arn,wrapped,local,previous);}
  @Bean WebhookReceipts webhookReceipts(JdbcTemplate jdbc){return new JdbcWebhookReceipts(jdbc,Clock.systemUTC());}
  @Bean RefundOperations refundOperations(JdbcTemplate jdbc,PlatformTransactionManager manager,PaystackAdapter provider,OrderPolicy binding,PaymentPolicy policy,@Value("${NIGSON_PAYSTACK_INTEGRATION_ID:}")String integration,AuditWriter audit,PlatformStore events,io.micrometer.core.instrument.MeterRegistry metrics){return new RefundCoordinator(jdbc,manager,provider,binding,policy,integration,audit,events,Clock.systemUTC(),metrics);}
  @Bean WebhookIngress webhookIngress(WebhookReceipts receipts,PaystackWebhookProtocol protocol,PaymentPayloadCipher cipher,OrderPolicy binding,PaymentPolicy policy,JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics){return new WebhookIngress(receipts,protocol,cipher,binding,policy,jdbc,manager,audit,Clock.systemUTC(),metrics);}
  @Bean PaymentReceiptProcessor paymentReceiptProcessor(WebhookReceipts receipts,PaystackWebhookProtocol protocol,PaymentPayloadCipher cipher,OrderPolicy binding,PaymentPolicy policy,CheckoutPayments payments,RefundOperations refunds,JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics){return new PaymentReceiptProcessor(receipts,protocol,cipher,binding,policy,payments,refunds,jdbc,manager,audit,metrics);}
  @Bean PaymentReconciler paymentReconciler(JdbcTemplate jdbc,PlatformStore commands,OrderPolicy binding,PaymentPolicy policy,CheckoutPayments payments,RefundOperations refunds,PaymentReceiptProcessor receipts){return new PaymentReconciler(jdbc,commands,binding,policy,payments,refunds,receipts,Clock.systemUTC());}
  @Bean RefundApprovedHandler refundApprovedHandler(PaymentPolicy policy,OrderPolicy binding){return new RefundApprovedHandler(policy,binding);}
  @Bean PaymentExpiry paymentExpiry(JdbcTemplate jdbc,PlatformTransactionManager manager,OrderPolicy binding,PaymentPolicy policy,CheckoutPayments payments,OrderRepository orders,com.nigson.commerce.inventory.StockLedger stock,com.nigson.commerce.cart.CartCheckout carts,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics){return new PaymentExpiry(jdbc,manager,binding,policy,payments,orders,stock,carts,audit,Clock.systemUTC(),metrics);}
  @Configuration(proxyBeanMethods=false)
  @org.springframework.scheduling.annotation.EnableScheduling
  @ConditionalOnProperty(name="NIGSON_PAYMENT_ENABLED",havingValue="true")
  static class Polling {
   @ConditionalOnProperty(name="NIGSON_PAYMENT_WORKER_ENABLED",havingValue="true",matchIfMissing=true)
   @Bean PaymentWorker paymentWorker(JdbcTemplate jdbc,OrderPolicy binding,PaymentPolicy policy,CheckoutPayments payments,RefundOperations refunds,PaymentReceiptProcessor receipts,PaymentExpiry expiry,@Value("${NIGSON_PAYMENT_WORKER_BATCH:25}")int limit){return new PaymentWorker(jdbc,binding,policy,payments,refunds,receipts,expiry,Clock.systemUTC(),limit);}
  }
 }
}
