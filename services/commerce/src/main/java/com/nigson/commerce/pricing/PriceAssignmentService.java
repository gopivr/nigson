package com.nigson.commerce.pricing;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.commerce.platform.PlatformStore;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;

/** Atomic BP3 organization assignment and scoped wholesale rule replacement. */
public final class PriceAssignmentService {
 private final JdbcTemplate jdbc;private final PricingAuthority authority;private final PlatformStore commands;private final AuditWriter audit;private final Clock clock;private final io.micrometer.core.instrument.MeterRegistry metrics;
 public PriceAssignmentService(JdbcTemplate jdbc,PricingAuthority authority,PlatformStore commands,AuditWriter audit,Clock clock,io.micrometer.core.instrument.MeterRegistry metrics){this.jdbc=jdbc;this.authority=authority;this.commands=commands;this.audit=audit;this.clock=clock;this.metrics=metrics;}

 public PricingValues.Assignment assign(ActorAssertion.Actor actor,UUID organization,long expected,PricingValues.AssignmentInput input,String key,UUID correlation){
  authority.require(actor,"SALES");if(authority.phase()<3)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(expected<0||expected==Long.MAX_VALUE)throw ApiValues.invalid();ApiValues.idempotencyKey(key);if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
  var request=Map.of("organizationId",organization,"expectedVersion",expected,"assignment",input);
  try{var response=commands.executeLocal(actor.party().toString(),"API-067",key,EventCodec.JSON.valueToTree(request),db->{jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");authority.stabilize(actor,"SALES");var value=replace(organization,expected,input,actor.party().toString());audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.PRICE_ASSIGNMENT,AuditValues.Resource.PRICE_BOOK,value.organizationId(),correlation,AuditValues.Reason.CONFIGURATION_CHANGE,null,Map.of("marketId",input.marketId(),"priceBookId",value.priceBookId(),"revision",value.revision(),"minimumRuleCount",input.minimumRules().size()),null);event(value,input.marketId(),actor.party().toString(),correlation);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.pricing.commands","operation","ASSIGNMENT").increment();}});return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(value),value.organizationId());});return EventCodec.JSON.treeToValue(response.body(),PricingValues.Assignment.class);}catch(org.springframework.dao.DataAccessException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql&&Set.of("55P03","57014").contains(sql.getSQLState()))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);throw failure;}
 }

 private PricingValues.Assignment replace(UUID organization,long expected,PricingValues.AssignmentInput input,String actor){
  jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(r,n)->1,"price-assignment:"+organization+":"+input.marketId());
  var org=jdbc.query("SELECT status FROM commerce.organization WHERE id=? FOR UPDATE",(r,n)->r.getString(1),organization);if(org.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);
  var markets=jdbc.query("SELECT m.currency_code,c.minor_unit FROM commerce.market m JOIN commerce.currency c ON c.code=m.currency_code WHERE m.id=? AND m.code='NG' AND m.enabled AND c.active AND c.code='NGN' AND c.minor_unit=2",(r,n)->new Object[]{r.getString(1),r.getInt(2)},input.marketId());if(markets.size()!=1)throw PricingValues.rule();String currency=(String)markets.getFirst()[0];int minor=(Integer)markets.getFirst()[1];
  var books=jdbc.query("SELECT id FROM commerce.price_book WHERE id=? AND active AND segment='DISTRIBUTOR' AND currency_code=? AND effective_from<=? AND (effective_until IS NULL OR effective_until>?) FOR SHARE",(r,n)->r.getObject(1,UUID.class),input.priceBookId(),currency,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));if(books.size()!=1)throw PricingValues.rule();
  validateRules(input,minor);
  var rows=jdbc.query("SELECT id,version FROM commerce.price_assignment WHERE organization_id=? AND market_id=? ORDER BY active DESC,updated_at DESC,id LIMIT 1 FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getLong(2)},organization,input.marketId());UUID id;long next;
  if(rows.isEmpty()){if(expected!=0)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);id=EventCodec.uuid7(clock.instant());next=1;jdbc.update("INSERT INTO commerce.price_assignment(id,created_by,updated_by,version,organization_id,market_id,price_book_id,active) VALUES (?,?,?,?,?,?,?,true)",id,actor,actor,next,organization,input.marketId(),input.priceBookId());}
  else{var row=rows.getFirst();id=(UUID)row[0];long version=(Long)row[1];if(version!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);if(version==Long.MAX_VALUE)throw PricingValues.rule();next=version+1;jdbc.update("UPDATE commerce.price_assignment SET price_book_id=?,active=true,version=?,updated_by=?,updated_at=? WHERE id=?",input.priceBookId(),next,actor,Timestamp.from(clock.instant()),id);}
  jdbc.update("DELETE FROM commerce.wholesale_rule WHERE organization_id=? AND market_id=?",organization,input.marketId());
  for(var rule:input.minimumRules())jdbc.update("INSERT INTO commerce.wholesale_rule(id,created_by,updated_by,organization_id,sku_id,market_id,minimum_quantity,pack_multiple,minimum_order_amount,rule_revision) VALUES (?,?,?,?,?,?,?,?,?,?)",EventCodec.uuid7(clock.instant()),actor,actor,organization,rule.skuId(),input.marketId(),rule.minimumQuantity()==null?null:new BigDecimal(rule.minimumQuantity()),rule.packMultiple()==null?null:new BigDecimal(rule.packMultiple()),rule.minimumOrderAmount()==null?null:new BigDecimal(rule.minimumOrderAmount()),next);
  return new PricingValues.Assignment(organization,input.priceBookId(),next);
 }

 private void validateRules(PricingValues.AssignmentInput input,int minor){
  var seen=new HashSet<String>();for(var rule:input.minimumRules()){
   if(!seen.add(rule.skuId()==null?"*":rule.skuId().toString()))throw PricingValues.rule();
   if(rule.minimumQuantity()==null&&rule.packMultiple()==null&&rule.minimumOrderAmount()==null)throw ApiValues.invalid();
   if(rule.skuId()!=null){var scales=jdbc.query("SELECT s.quantity_scale FROM commerce.sku s JOIN commerce.product p ON p.id=s.product_id JOIN commerce.product_market_eligibility e ON e.product_id=p.id WHERE s.id=? AND s.active AND s.deleted_at IS NULL AND p.status='ACTIVE' AND p.deleted_at IS NULL AND e.market_id=? AND e.eligible FOR SHARE OF s,p,e",(r,n)->r.getInt(1),rule.skuId(),input.marketId());if(scales.size()!=1)throw PricingValues.rule();int scale=scales.getFirst();if(rule.minimumQuantity()!=null)PricingValues.quantity(rule.minimumQuantity(),scale);if(rule.packMultiple()!=null&&PricingValues.quantity(rule.packMultiple(),scale).signum()<=0)throw PricingValues.rule();}
   else{if(rule.minimumQuantity()!=null&&new BigDecimal(rule.minimumQuantity()).signum()<=0||rule.packMultiple()!=null&&new BigDecimal(rule.packMultiple()).signum()<=0)throw PricingValues.rule();}
   if(rule.minimumOrderAmount()!=null)PricingValues.minor(rule.minimumOrderAmount(),minor);
  }
 }

 private void event(PricingValues.Assignment value,UUID market,String actor,UUID correlation){var envelope=new LinkedHashMap<String,Object>();envelope.put("event_id",EventCodec.uuid7(clock.instant()));envelope.put("event_type","pricing.changed.v1");envelope.put("schema_version",1);envelope.put("producer","commerce");envelope.put("occurred_at",clock.instant());envelope.put("aggregate_id",value.organizationId());envelope.put("aggregate_version",value.revision());envelope.put("organization_id",value.organizationId());envelope.put("correlation_id",correlation);var span=io.opentelemetry.api.trace.Span.current().getSpanContext();envelope.put("traceparent",span.isValid()?"00-"+span.getTraceId()+"-"+span.getSpanId()+"-"+span.getTraceFlags().asHex():null);envelope.put("payload",Map.of("entity_type","ASSIGNMENT","entity_id",value.organizationId(),"revision",value.revision(),"market_id",market));commands.append(EventCodec.JSON.writeValueAsString(envelope),actor);}
}
