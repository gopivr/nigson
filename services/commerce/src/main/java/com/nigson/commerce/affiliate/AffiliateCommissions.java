package com.nigson.commerce.affiliate;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Immutable financial entries from verified local facts; never calls a provider. */
public final class AffiliateCommissions {
 private static final BigDecimal RATE=new BigDecimal("0.05");
 private final JdbcTemplate jdbc;private final AuditWriter audit;private final Clock clock;private final int phase;private final org.springframework.transaction.support.TransactionTemplate reads;
 public AffiliateCommissions(JdbcTemplate jdbc,AuditWriter audit,Clock clock,int phase,org.springframework.transaction.PlatformTransactionManager manager){this.jdbc=jdbc;this.audit=audit;this.clock=clock;this.phase=phase;reads=new org.springframework.transaction.support.TransactionTemplate(manager);reads.setReadOnly(true);reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(5);}
 private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");}
 public void orderSettled(UUID order,UUID correlation){
  if(phase<3)return;transaction();
  var rows=jdbc.queryForList("SELECT party_id,organization_id,currency_code,subtotal-discount_total AS basis,commercial_snapshot::text AS snapshot FROM commerce.sales_order o WHERE id=? AND payment_status IN ('PAID','PARTIALLY_REFUNDED') AND EXISTS(SELECT 1 FROM commerce.payment_attempt a JOIN commerce.payment_transaction t ON t.attempt_id=a.id WHERE a.order_id=o.id AND a.state='SUCCEEDED' AND a.verified_at IS NOT NULL AND t.provider_status='success') FOR UPDATE",order);
  if(rows.size()!=1)return;var row=rows.getFirst();if(row.get("organization_id")!=null||!"NGN".equals(row.get("currency_code")))return;
  var attribution=EventCodec.JSON.readTree((String)row.get("snapshot")).path("affiliateAttribution");
  // Old orders without an agreed frozen commission policy are not retroactively repriced.
  if(!"0.05".equals(attribution.path("commissionRate").asString())||attribution.path("holdDays").asInt()!=7)return;
  UUID affiliate;try{affiliate=UUID.fromString(attribution.path("affiliatePartyId").asString());}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}
  if(affiliate.equals(row.get("party_id")))return;
  jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),affiliate);
  BigDecimal basis=((BigDecimal)row.get("basis")).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP),amount=basis.multiply(RATE).setScale(2,RoundingMode.HALF_UP);
  if(amount.signum()==0)return;
  append(affiliate,order,"EARN",amount,basis,"affiliate:order:"+order,null,null,correlation);
 }
 public void refundSettled(UUID refund,UUID correlation){
  if(phase<3)return;transaction();
  var orders=jdbc.query("SELECT order_id FROM commerce.refund WHERE id=? AND state='SUCCEEDED'",(r,n)->r.getObject(1,UUID.class),refund);if(orders.size()!=1)return;UUID order=orders.getFirst();
  jdbc.query("SELECT id FROM commerce.sales_order WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),order);
  var earns=jdbc.queryForList("SELECT id,party_id,amount,merchandise_basis FROM commerce.affiliate_commission_entry WHERE order_id=? AND kind='EARN'",order);
  if(earns.isEmpty())return;
  BigDecimal refunded=jdbc.queryForObject("SELECT coalesce(sum(CASE WHEN ol.line_total>0 THEN rl.amount*(ol.line_total-ol.tax_amount)/ol.line_total ELSE 0 END),0) FROM commerce.refund_line rl JOIN commerce.refund r ON r.id=rl.refund_id JOIN commerce.order_line ol ON ol.id=rl.order_line_id WHERE r.order_id=? AND r.state='SUCCEEDED'",BigDecimal.class,order);
  for(var earn:earns){jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),earn.get("party_id"));UUID id=(UUID)earn.get("id");BigDecimal original=(BigDecimal)earn.get("amount"),basis=(BigDecimal)earn.get("merchandise_basis");BigDecimal refundedBasis=refunded.max(BigDecimal.ZERO).min(basis);
   BigDecimal target=refundedBasis.multiply(RATE).setScale(2,RoundingMode.HALF_UP).min(original);
   BigDecimal prior=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM commerce.affiliate_commission_entry WHERE reverses_id=? AND kind='REVERSE'",BigDecimal.class,id);
   BigDecimal delta=target.subtract(prior).max(BigDecimal.ZERO);if(delta.signum()>0)append((UUID)earn.get("party_id"),order,"REVERSE",delta,refundedBasis,"affiliate:refund:"+refund+":"+id,id,refund,correlation);
  }
 }
 private void append(UUID party,UUID order,String kind,BigDecimal amount,BigDecimal basis,String key,UUID reverses,UUID refund,UUID correlation){
  UUID id=EventCodec.uuid7(clock.instant());int inserted=jdbc.update("INSERT INTO commerce.affiliate_commission_entry(id,created_at,created_by,party_id,order_id,kind,amount,merchandise_basis,rate,business_key,reverses_id,refund_id) VALUES (?,?,'affiliate-engine',?,?,?,?,?,0.05,?,?,?) ON CONFLICT(business_key) DO NOTHING",id,java.sql.Timestamp.from(clock.instant()),party,order,kind,amount,basis,key,reverses,refund);
  if(inserted==1)audit.append("affiliate-engine",AuditValues.ActorKind.SYSTEM,AuditValues.Action.REWARD_ISSUE,AuditValues.Resource.REFERRAL,id,correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("affiliateCommission",true,"kind",kind,"orderId",order,"amount",amount.toPlainString(),"currency","NGN"),null);
 }

 public record Earning(UUID orderId,String orderNumber,String earned,String reversed,String net,String state,Instant availableAt){}
 public record Earnings(String currency,String pending,String available,String held,String reversed,List<Earning> items,int page,int size,long totalElements,long totalPages){}
 private static final String PROJECTION="""
  WITH commissions AS (
   SELECT e.order_id,e.amount AS earned,coalesce((SELECT sum(r.amount) FROM commerce.affiliate_commission_entry r WHERE r.order_id=e.order_id AND r.reverses_id=e.id AND r.kind='REVERSE'),0) AS reversed
   FROM commerce.affiliate_commission_entry e WHERE e.party_id=? AND e.kind='EARN'
  ), facts AS (
   SELECT c.*,o.order_number,o.payment_status,o.status,o.fulfillment_status,
    (SELECT max(s.delivered_at) + interval '7 days' FROM commerce.shipment s WHERE s.order_id=o.id AND s.status='DELIVERED') AS available_at,
    EXISTS(SELECT 1 FROM commerce.refund r WHERE r.order_id=o.id AND r.state IN ('REQUESTED','APPROVED','SUBMITTING','PENDING','UNKNOWN')) AS refund_hold,
    EXISTS(SELECT 1 FROM commerce.shipment s WHERE s.order_id=o.id AND s.status<>'CANCELLED' AND (s.status<>'DELIVERED' OR s.delivered_at IS NULL)) AS undelivered,
    o.created_at
   FROM commissions c JOIN commerce.sales_order o ON o.id=c.order_id
  ), balances AS (
   SELECT *,greatest(earned-reversed,0) AS net,
    CASE WHEN earned<=reversed THEN 'REVERSED'
     WHEN refund_hold OR status IN ('CANCELLED','REFUNDING','REFUNDED','PAYMENT_HOLD') OR payment_status NOT IN ('PAID','PARTIALLY_REFUNDED') OR fulfillment_status IN ('HOLD','CANCELLED') THEN 'HELD'
     WHEN fulfillment_status='DELIVERED' AND NOT undelivered AND available_at<=? THEN 'AVAILABLE'
     ELSE 'PENDING' END AS state
   FROM facts
  )
  """;
 public Earnings earnings(UUID party,int page){
  return reads.execute(s->read(party,page));
 }
 private Earnings read(UUID party,int page){
  if(phase<3)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(page<0||page>10000)throw ApiValues.invalid();var now=java.sql.Timestamp.from(clock.instant());
  var totals=jdbc.queryForMap(PROJECTION+"SELECT count(*) AS count,coalesce(sum(net) FILTER(WHERE state='PENDING'),0) AS pending,coalesce(sum(net) FILTER(WHERE state='AVAILABLE'),0) AS available,coalesce(sum(net) FILTER(WHERE state='HELD'),0) AS held,coalesce(sum(reversed),0) AS reversed FROM balances",party,now);
  var items=jdbc.query(PROJECTION+"SELECT * FROM balances ORDER BY created_at DESC,order_id LIMIT 20 OFFSET ?",(r,n)->new Earning(r.getObject("order_id",UUID.class),r.getString("order_number"),money(r.getBigDecimal("earned")),money(r.getBigDecimal("reversed")),money(r.getBigDecimal("net")),r.getString("state"),r.getTimestamp("available_at")==null?null:r.getTimestamp("available_at").toInstant()),party,now,page*20);
  long count=((Number)totals.get("count")).longValue();return new Earnings("NGN",money((BigDecimal)totals.get("pending")),money((BigDecimal)totals.get("available")),money((BigDecimal)totals.get("held")),money((BigDecimal)totals.get("reversed")),items,page,20,count,(count+19)/20);
 }
 private String money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP).toPlainString();}
}
