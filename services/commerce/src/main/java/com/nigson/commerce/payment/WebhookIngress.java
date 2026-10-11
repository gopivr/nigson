package com.nigson.commerce.payment;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.nigson.commerce.order.OrderPolicy;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
/** The synchronous callback only authenticates and durably acknowledges encrypted receipt evidence. */
public final class WebhookIngress {
 private final WebhookReceipts receipts;private final PaystackWebhookProtocol protocol;private final PaymentPayloadCipher cipher;private final OrderPolicy binding;private final PaymentPolicy policy;private final TransactionTemplate tx;private final JdbcTemplate jdbc;private final AuditWriter audit;private final Clock clock;private final io.micrometer.core.instrument.MeterRegistry metrics;
 public WebhookIngress(WebhookReceipts receipts,PaystackWebhookProtocol protocol,PaymentPayloadCipher cipher,OrderPolicy binding,PaymentPolicy policy,JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,Clock clock,io.micrometer.core.instrument.MeterRegistry metrics){this.receipts=receipts;this.protocol=protocol;this.cipher=cipher;this.binding=binding;this.policy=policy;this.jdbc=jdbc;this.audit=audit;this.clock=clock;this.metrics=metrics;tx=new TransactionTemplate(manager);tx.setTimeout(2);}
 public boolean receive(byte[] raw,String signature,UUID correlation){policy.require();if(!binding.enabled())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);ApiValues.uuid(correlation==null?null:correlation.toString());var event=protocol.authenticate(raw,signature);UUID id=EventCodec.uuid7(clock.instant());byte[] encrypted=cipher.encrypt(id,binding.merchantAccount(),binding.mode(),event.bodyHash(),raw);return tx.execute(status->{jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");var stored=receipts.receive(id,binding.merchantAccount(),event,encrypted);if(stored.created()){audit.append("paystack-receipt",AuditValues.ActorKind.SYSTEM,AuditValues.Action.PAYMENT_RECEIPT,AuditValues.Resource.PAYMENT_RECEIPT,stored.receipt().id(),correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("state",stored.receipt().state(),"version",stored.receipt().version(),"providerMode",binding.mode()),null);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.payment.receipts","state",stored.receipt().state()).increment();}});}return true;});}
}
