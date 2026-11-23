package com.nigson.commerce.order;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.nigson.commerce.inventory.StockLedger;
import com.nigson.commerce.cart.CartCheckout;
import com.nigson.commerce.promotion.CouponUsage;
import com.nigson.commerce.payment.*;
import com.nigson.commerce.fulfillment.CarrierCancellationValues;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Owner-local saga. Every provider call occurs outside the order/stock transaction. */
public final class OrderCancellationWorker {
 private static final String ACTOR="order-cancellation-worker";
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final CheckoutPayments payments;
 private final StockLedger stock;private final CartCheckout carts;private final PaymentRepository refunds;
 private final PlatformStore events;private final AuditWriter audit;private final Clock clock;
 public OrderCancellationWorker(JdbcTemplate jdbc,PlatformTransactionManager manager,CheckoutPayments payments,StockLedger stock,CartCheckout carts,PaymentRepository refunds,PlatformStore events,AuditWriter audit,Clock clock){this.jdbc=jdbc;this.payments=payments;this.stock=stock;this.carts=carts;this.refunds=refunds;this.events=events;this.audit=audit;this.clock=clock;tx=new TransactionTemplate(manager);tx.setTimeout(5);}
 private java.sql.Timestamp stamp(Instant at){return java.sql.Timestamp.from(at);}
 @Scheduled(fixedDelayString="${NIGSON_ORDER_CANCELLATION_DELAY_MS:5000}")public void scheduled(){try{poll();}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("ORDER_CANCELLATION_RECOVERY_REQUIRED");}}
 public int poll(){var ids=jdbc.query("SELECT c.order_id FROM commerce.order_cancellation c JOIN commerce.sales_order o ON o.id=c.order_id WHERE (c.state NOT IN ('REJECTED','CANCELLED') OR c.state='CANCELLED' AND c.refund_id IS NULL AND o.payment_status IN ('PAID','PARTIALLY_REFUNDED')) AND c.next_attempt_at<=? ORDER BY c.next_attempt_at,c.id LIMIT 20",(r,n)->r.getObject(1,UUID.class),stamp(clock.instant()));for(UUID order:ids){try{process(order);}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("ORDER_CANCELLATION_REQUIRES_REVIEW");}}return ids.size();}
 public void process(UUID order){
  // Inspect may settle a racing payment. Cancellation exclusion is already durable.
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.payment_attempt WHERE order_id=? AND state NOT IN ('SUCCEEDED','FAILED','CANCELLED'))",Boolean.class,order))){try{payments.inspect(order,UUID.randomUUID());}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("ORDER_CANCELLATION_PAYMENT_CHECK_PENDING");}}
  tx.executeWithoutResult(status->{jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='3s'");
   var o=jdbc.queryForMap("SELECT * FROM commerce.sales_order WHERE id=? FOR UPDATE",order);
   var c=jdbc.queryForMap("SELECT * FROM commerce.order_cancellation WHERE order_id=? FOR UPDATE",order);
   if(c.get("state").equals("REJECTED"))return;UUID id=(UUID)c.get("id");
   jdbc.update("UPDATE commerce.order_cancellation SET next_attempt_at=?,updated_at=?,updated_by=? WHERE id=?",stamp(clock.instant().plusSeconds(30)),stamp(clock.instant()),ACTOR,id);
   if(!c.get("state").equals("CANCELLED")){
    var deliveries=jdbc.queryForList("SELECT * FROM commerce.shipment WHERE order_id=? ORDER BY id FOR UPDATE",order);
    if(deliveries.stream().anyMatch(s->s.get("dispatched_at")!=null||Set.of("SHIPPED","DELIVERED").contains(s.get("status")))){state(id,"REJECTED","CARRIER_ALREADY_DELIVERED");return;}
    boolean waiting=false;
    for(var s:deliveries){if(s.get("status").equals("CANCELLED"))continue;
     if(!"PANDAGO".equals(s.get("provider"))||s.get("booking_request_id")==null){state(id,"WAITING_CARRIER","CARRIER_APPROVAL_REQUIRED");return;}
     UUID shipment=(UUID)s.get("id");var prior=jdbc.queryForList("SELECT state FROM commerce.carrier_cancellation WHERE shipment_id=? ORDER BY created_at DESC,id DESC LIMIT 1",shipment);
     if(!prior.isEmpty()&&prior.getFirst().get("state").equals("REJECTED")){state(id,"REJECTED","CARRIER_CANCEL_REJECTED");return;}
     if(prior.isEmpty()){
      UUID cancellation=EventCodec.uuid7(clock.instant());String reference=s.get("provider_reference")==null?s.get("booking_request_id").toString():s.get("provider_reference").toString();
      String mode=EventCodec.JSON.readTree(s.get("package_snapshot").toString()).path("mode").asString();Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      var command=new CarrierCancellationValues.Command(cancellation,shipment,(UUID)s.get("booking_request_id"),reference,mode,now);
      jdbc.update("INSERT INTO commerce.carrier_cancellation(id,created_by,updated_by,shipment_id,booking_request_id,reference,mode,reason,request_hash,requested_at,next_attempt_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",cancellation,ACTOR,ACTOR,shipment,command.bookingRequestId(),reference,mode,c.get("reason"),CarrierCancellationValues.hash(command),stamp(now),stamp(now));
     }
     waiting=true;
    }
    if(waiting){state(id,"WAITING_CARRIER",null);return;}
    if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.payment_attempt WHERE order_id=? AND (state NOT IN ('SUCCEEDED','FAILED','CANCELLED') OR state IN ('FAILED','CANCELLED') AND verified_at IS NULL))",Boolean.class,order))){state(id,"WAITING_PAYMENT","PROVIDER_OUTCOME_UNKNOWN");return;}
    String before=o.get("status").toString();
    jdbc.update("UPDATE commerce.sales_order SET status='CANCELLED',fulfillment_status='CANCELLED',payment_status=CASE WHEN payment_status='PENDING' THEN 'FAILED' ELSE payment_status END,version=version+1,updated_at=?,updated_by=? WHERE id=?",stamp(clock.instant()),ACTOR,order);
    var reservations=jdbc.queryForList("SELECT status FROM commerce.inventory_reservation WHERE order_id=?",order);
    if(reservations.stream().anyMatch(r->r.get("status").equals("RESERVED"))&&!reservations.stream().allMatch(r->r.get("status").equals("RESERVED")))throw OrderValues.rule();
    if(!reservations.isEmpty()&&reservations.stream().allMatch(r->r.get("status").equals("RESERVED")))stock.release(order,id,ACTOR,id);
    else if(reservations.stream().anyMatch(r->r.get("status").equals("ALLOCATED"))){
     var lines=jdbc.query("SELECT l.id,r.balance_id,l.quantity-COALESCE((SELECT -sum(m.delta_allocated) FROM commerce.inventory_movement m WHERE m.order_id=l.order_id AND m.order_line_id=l.id AND m.return_kind='UNSHIPPED_CANCEL'),0) quantity FROM commerce.order_line l JOIN commerce.inventory_balance b ON b.sku_id=l.sku_id JOIN commerce.inventory_reservation r ON r.balance_id=b.id AND r.order_id=l.order_id WHERE l.order_id=? AND r.status='ALLOCATED' ORDER BY r.balance_id,l.id",(r,n)->new StockLedger.Line(r.getObject(1,UUID.class),r.getObject(2,UUID.class),com.nigson.commerce.pricing.PricingValues.decimal(r.getBigDecimal(3))),order);
     if(!lines.isEmpty())stock.returned(order,lines,StockLedger.ReturnKind.UNSHIPPED_CANCEL,id.toString(),"Confirmed customer order cancellation",id,ACTOR,id);
    }
    CouponUsage.release(jdbc,clock,order,ACTOR);
    if(o.get("cart_id")!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT status IN ('CHECKOUT_PENDING','EXPIRED') FROM commerce.cart WHERE id=?",Boolean.class,o.get("cart_id")))&&!Set.of("PAID","PARTIALLY_REFUNDED","REFUNDED").contains(o.get("payment_status")))carts.expired(order,id);
    jdbc.update("INSERT INTO commerce.order_status_history(id,created_by,order_id,previous_status,new_status,reason) VALUES (?,?,?,?,'CANCELLED',?)",EventCodec.uuid7(clock.instant()),ACTOR,order,before,"Confirmed customer order cancellation");
    state(id,"CANCELLED",null);event(order,o,id,"order.cancelled.v1",Map.of("order_id",order,"reason_code","CUSTOMER_REQUEST"));
   }
   requestRefund(order,o,c,id);
  });
 }
 private void requestRefund(UUID order,Map<String,Object> o,Map<String,Object> c,UUID id){
  if(!Set.of("PAID","PARTIALLY_REFUNDED").contains(o.get("payment_status"))||c.get("refund_id")!=null)return;
  var reserved=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM commerce.refund WHERE order_id=? AND state NOT IN ('FAILED','REJECTED')",BigDecimal.class,order);
  BigDecimal amount=((BigDecimal)o.get("grand_total")).subtract(reserved);if(amount.signum()<=0){jdbc.update("UPDATE commerce.order_cancellation SET refund_id=(SELECT id FROM commerce.refund WHERE order_id=? AND state NOT IN ('FAILED','REJECTED') ORDER BY created_at DESC,id DESC LIMIT 1),version=version+1 WHERE id=?",order,id);return;}
  long version=jdbc.queryForObject("SELECT version FROM commerce.sales_order WHERE id=?",Long.class,order);
  var refund=refunds.request(order,version,new PaymentValues.RefundInput(PaymentValues.money(amount.stripTrailingZeros().toPlainString(),true),"Customer order cancellation",List.of()),EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("cancellation",id))),(UUID)c.get("requested_by"));
  jdbc.update("UPDATE commerce.order_cancellation SET refund_id=?,version=version+1 WHERE id=?",refund.id(),id);
  audit.append(ACTOR,AuditValues.ActorKind.SYSTEM,AuditValues.Action.REFUND_REQUEST,AuditValues.Resource.REFUND,refund.id(),id,AuditValues.Reason.RECONCILIATION,null,Map.of("state",refund.state(),"version",refund.version()),null);
  event(refund.id(),o,id,"refund.requested.v1",Map.of("refund_id",refund.id(),"order_id",order));
 }
 private void state(UUID id,String state,String error){int changed=jdbc.update("UPDATE commerce.order_cancellation SET state=?,error_code=?,version=version+1,updated_at=?,updated_by=? WHERE id=? AND (state<>? OR error_code IS DISTINCT FROM ?)",state,error,stamp(clock.instant()),ACTOR,id,state,error);if(changed==0)return;jdbc.update("UPDATE commerce.sales_order SET version=version+1,updated_at=?,updated_by=? WHERE id=(SELECT order_id FROM commerce.order_cancellation WHERE id=?)",stamp(clock.instant()),ACTOR,id);audit.append(ACTOR,AuditValues.ActorKind.SYSTEM,AuditValues.Action.ORDER_CANCELLATION,AuditValues.Resource.ORDER,jdbc.queryForObject("SELECT order_id FROM commerce.order_cancellation WHERE id=?",UUID.class,id),id,AuditValues.Reason.RECONCILIATION,null,Map.of("state",state),null);}
 private void event(UUID aggregate,Map<String,Object> order,UUID correlation,String type,Map<String,Object> payload){var body=new LinkedHashMap<String,Object>();body.put("event_id",EventCodec.uuid7(clock.instant()));body.put("event_type",type);body.put("schema_version",1);body.put("producer","commerce");body.put("occurred_at",clock.instant());body.put("aggregate_id",aggregate);body.put("aggregate_version",type.equals("refund.requested.v1")?0:jdbc.queryForObject("SELECT version FROM commerce.sales_order WHERE id=?",Long.class,aggregate));body.put("organization_id",order.get("organization_id"));body.put("correlation_id",correlation);body.put("traceparent",null);body.put("payload",payload);events.append(EventCodec.JSON.writeValueAsString(body),ACTOR);}
}
