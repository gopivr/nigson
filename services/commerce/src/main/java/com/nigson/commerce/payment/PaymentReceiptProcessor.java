package com.nigson.commerce.payment;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.nigson.commerce.order.*;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;

/** Decrypt owned evidence, independently verify outside locks, then acknowledge local truth. */
public final class PaymentReceiptProcessor {
 private final WebhookReceipts receipts;private final PaystackWebhookProtocol protocol;private final PaymentPayloadCipher cipher;private final OrderPolicy binding;private final PaymentPolicy policy;private final CheckoutPayments payments;private final RefundOperations refunds;private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final AuditWriter audit;private final io.micrometer.core.instrument.MeterRegistry metrics;
 public PaymentReceiptProcessor(WebhookReceipts receipts,PaystackWebhookProtocol protocol,PaymentPayloadCipher cipher,OrderPolicy binding,PaymentPolicy policy,CheckoutPayments payments,RefundOperations refunds,JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics){this.receipts=receipts;this.protocol=protocol;this.cipher=cipher;this.binding=binding;this.policy=policy;this.payments=payments;this.refunds=refunds;this.jdbc=jdbc;this.audit=audit;this.metrics=metrics;tx=new TransactionTemplate(manager);tx.setTimeout(2);}
 private String scalar(JsonNode data,String name){var n=data.get(name);if(n==null||!(n.isString()||n.isIntegralNumber()))return null;String s=n.isString()?n.asString():n.toString();return s.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")?s:null;}
 public String process(UUID id,UUID correlation){policy.require();ApiValues.uuid(id==null?null:id.toString());ApiValues.uuid(correlation==null?null:correlation.toString());if(!binding.enabled())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  WebhookReceipts.Pending pending;try{pending=receipts.pending(id);}catch(Problem.Failure failure){if(!failure.getMessage().equals("INVALID_TRANSITION"))throw failure;return terminal(id);}
  if(!pending.verifiedSignature())return finish(pending,"REJECTED","PAYLOAD_INVALID",null,correlation);
  if(!binding.merchantAccount().equals(pending.merchantAccount()))return finish(pending,"REJECTED","PROVIDER_MODE_MISMATCH",null,correlation);
  PaystackWebhookProtocol.Event event;try{event=protocol.stored(cipher.decrypt(id,pending.merchantAccount(),binding.mode(),pending.bodyHash(),pending.ciphertext()));}catch(IllegalArgumentException|Problem.Failure invalid){return finish(pending,"REJECTED","PAYLOAD_INVALID",null,correlation);}
  if(!event.matchingMode())return finish(pending,"REJECTED","PROVIDER_MODE_MISMATCH",null,correlation);
  if(!event.recognized())return finish(pending,"REJECTED","PAYLOAD_INVALID",null,correlation);
  String object=scalar(event.data(),"id");if(object==null)return finish(pending,"REJECTED","PAYLOAD_INVALID",null,correlation);
  if(event.type().equals("charge.success")){
   String reference=scalar(event.data(),"reference");if(reference==null)return finish(pending,"REJECTED","PAYLOAD_INVALID",null,correlation);
   var matched=jdbc.queryForList("SELECT id,order_id FROM commerce.payment_attempt WHERE provider='PAYSTACK' AND merchant_account=? AND provider_mode=? AND reference=?",binding.merchantAccount(),binding.mode(),reference);
   if(matched.size()!=1)return finish(pending,"REJECTED","UNMATCHED_REFERENCE",null,correlation);
   var a=matched.getFirst();UUID order=(UUID)a.get("order_id"),attempt=(UUID)a.get("id");payments.inspect(order,correlation);
   var truth=jdbc.queryForList("SELECT t.provider_transaction_id FROM commerce.payment_transaction t JOIN commerce.payment_attempt a ON a.id=t.attempt_id WHERE a.id=? AND a.state='SUCCEEDED' AND a.verified_at IS NOT NULL",attempt);
   if(truth.isEmpty())return finish(pending,"RETRY",null,null,correlation);
   if(truth.size()!=1||!object.equals(truth.getFirst().get("provider_transaction_id")))return finish(pending,"REJECTED","VERIFICATION_MISMATCH",null,correlation);
   return finish(pending,"APPLIED",null,new WebhookReceipts.Verification(order,attempt,null,object,binding.mode()),correlation);
  }
  String note=scalar(event.data(),"merchant_note");var matched=jdbc.queryForList("SELECT r.id,r.order_id,r.attempt_id FROM commerce.refund r JOIN commerce.payment_attempt a ON a.id=r.attempt_id WHERE a.provider='PAYSTACK' AND a.merchant_account=? AND a.provider_mode=? AND (r.provider_refund_id=? OR (?::text IS NOT NULL AND r.provider_reference=?)) ORDER BY r.id LIMIT 2",binding.merchantAccount(),binding.mode(),object,note,note);
  if(matched.size()!=1)return finish(pending,"REJECTED","UNMATCHED_REFERENCE",null,correlation);
  var r=matched.getFirst();UUID refund=(UUID)r.get("id");refunds.inspect(refund,correlation);var truth=jdbc.queryForMap("SELECT state,provider_refund_id FROM commerce.refund WHERE id=?",refund);
  if(!Set.of("PENDING","SUCCEEDED","FAILED").contains(truth.get("state")))return finish(pending,"RETRY",null,null,correlation);
  if(!object.equals(truth.get("provider_refund_id")))return finish(pending,"REJECTED","VERIFICATION_MISMATCH",null,correlation);
  return finish(pending,"APPLIED",null,new WebhookReceipts.Verification((UUID)r.get("order_id"),(UUID)r.get("attempt_id"),refund,object,binding.mode()),correlation);
 }
 private String terminal(UUID id){var state=jdbc.queryForObject("SELECT state FROM commerce.webhook_receipt WHERE id=? AND provider='PAYSTACK'",String.class,id);if(!Set.of("APPLIED","REJECTED").contains(state))throw PaymentValues.rule();return state;}
 private String finish(WebhookReceipts.Pending pending,String state,String reason,WebhookReceipts.Verification proof,UUID correlation){return tx.execute(s->{jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");var before=jdbc.queryForMap("SELECT state,version FROM commerce.webhook_receipt WHERE id=? FOR UPDATE",pending.id());var after=receipts.outcome(pending.id(),pending.merchantAccount(),pending.bodyHash(),state,reason,proof);if(((Number)before.get("version")).longValue()!=after.version()){audit.append("paystack-receipt-worker",AuditValues.ActorKind.SYSTEM,AuditValues.Action.PAYMENT_RECEIPT,AuditValues.Resource.PAYMENT_RECEIPT,pending.id(),correlation,AuditValues.Reason.RECONCILIATION,Map.of("state",before.get("state"),"version",before.get("version")),Map.of("state",after.state(),"version",after.version(),"providerMode",binding.mode()),null);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.payment.receipts","state",after.state()).increment();}});}return after.state();});}
}
