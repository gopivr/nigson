package com.nigson.commerce.authorization;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

/** Commerce-owned acknowledged distributor entitlement transition. */
public final class DistributorEntitlementService {
 private final JdbcTemplate jdbc;private final PlatformStore commands;private final AuditWriter audit;private final io.micrometer.core.instrument.MeterRegistry metrics;private final Clock clock;private final int phase;private final String entitlement;
 public DistributorEntitlementService(JdbcTemplate jdbc,PlatformStore commands,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,Clock clock,int phase,String entitlement){this.jdbc=jdbc;this.commands=commands;this.audit=audit;this.metrics=metrics;this.clock=clock;this.phase=phase;this.entitlement=entitlement;}
 public EntitlementValues.Acknowledgement execute(PrivateWorkload.Proof proof,EntitlementValues.Command input,long expectedVersion,String idempotencyKey,UUID correlation){
  if(phase<3)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
  if(proof==null||!proof.route().api().equals("API-117")||!proof.client().equals("nigson-experience")||!proof.route().scope().equals("entitlement.write"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  ApiValues.idempotencyKey(idempotencyKey);if(expectedVersion<0||expectedVersion==Long.MAX_VALUE||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
  if(entitlement==null||!entitlement.matches("[A-Z][A-Z0-9_]{1,39}"))throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
  var request=EventCodec.JSON.valueToTree(Map.of("command",input,"expectedVersion",expectedVersion,"idempotencyKey",idempotencyKey));
  try{
   var response=commands.executeLocal("service:nigson-experience","API-117",input.commandId().toString(),request,db->{
    jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(r,n)->1,"distributor-application:"+input.applicationId());
    String country=market(input.marketId(),input.countryCode(),input.decision()==EntitlementValues.Decision.ACTIVATE);if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party WHERE id=?)",Boolean.class,input.partyId())))throw new Problem.Failure(ProblemCode.NOT_FOUND);UUID organization;
    if(input.decision()==EntitlementValues.Decision.ACTIVATE){
     if(expectedVersion!=0)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
     var byApplication=jdbc.query("SELECT organization_id,status,version,approval_command_id FROM commerce.account_entitlement WHERE source_application_id=? AND entitlement=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getLong(3),r.getObject(4,UUID.class)},input.applicationId(),entitlement);
     if(!byApplication.isEmpty()){
      var row=byApplication.getFirst();if(!row[3].equals(input.commandId()))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
      return ack(input.commandId(),(UUID)row[0],(String)row[1],(Long)row[2]);
     }
     organization=organization(input,country);
     var prior=jdbc.query("SELECT id,status,source_application_id,version FROM commerce.account_entitlement WHERE organization_id=? AND entitlement=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getLong(4)},organization,entitlement);
     if(!prior.isEmpty())throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
     activateOrganization(organization,input.partyId());
     assignBook(organization,input,country);
     long revision=0;UUID id=EventCodec.uuid7(clock.instant());Timestamp now=Timestamp.from(clock.instant());
     jdbc.update("INSERT INTO commerce.account_entitlement(id,created_by,updated_by,version,organization_id,entitlement,status,source_application_id,approval_command_id,activated_at) VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?)",id,"nigson-experience","nigson-experience",revision,organization,entitlement,input.applicationId(),input.commandId(),now);
     return finish(input,organization,"ACTIVE",revision,idempotencyKey,correlation);
    }
    var rows=jdbc.query("SELECT id,organization_id,status,version,source_application_id FROM commerce.account_entitlement WHERE source_application_id=? AND entitlement=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getLong(4),r.getObject(5,UUID.class)},input.applicationId(),entitlement);
    if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);var row=rows.getFirst();organization=(UUID)row[1];
    if(!row[4].equals(input.applicationId()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    var orgCountry=jdbc.query("SELECT country_code,status FROM commerce.organization WHERE id=? FOR UPDATE",(r,n)->new String[]{r.getString(1),r.getString(2)},organization);if(orgCountry.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);
    if(!country.equals(orgCountry.getFirst()[0]))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    String state=(String)row[2];long version=(Long)row[3];if(version!=expectedVersion)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);if(state.equals("SUSPENDED"))return ack(input.commandId(),organization,state,version);if(!state.equals("ACTIVE"))throw EntitlementValues.transition();if(version==Long.MAX_VALUE)throw new Problem.Failure(ProblemCode.AUTHORIZATION_RULE_VIOLATION);
    long revision=version+1;Timestamp now=Timestamp.from(clock.instant());jdbc.update("UPDATE commerce.account_entitlement SET status='SUSPENDED',suspended_at=?,version=?,updated_at=?,updated_by='nigson-experience' WHERE id=? AND version=?",now,revision,now,row[0],version);
    return finish(input,organization,"SUSPENDED",revision,idempotencyKey,correlation);
   });return EventCodec.JSON.treeToValue(response.body(),EntitlementValues.Acknowledgement.class);
  }catch(PlatformStore.Conflict conflict){if(conflict.getMessage().equals("IDEMPOTENCY_HASH_MISMATCH"))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);}
 }
 private String market(UUID market,String requestedCountry,boolean activation){var rows=jdbc.query("SELECT code,currency_code,enabled FROM commerce.market WHERE id=? FOR SHARE",(r,n)->new Object[]{r.getString(1),r.getString(2),r.getBoolean(3)},market);if(rows.size()!=1||!rows.getFirst()[0].equals(requestedCountry)||!requestedCountry.equals("NG")||activation&&!((Boolean)rows.getFirst()[2]))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(activation){var currency=jdbc.query("SELECT minor_unit FROM commerce.currency WHERE code=? AND active FOR SHARE",(r,n)->r.getInt(1),rows.getFirst()[1]);if(currency.size()!=1||!rows.getFirst()[1].equals("NGN")||currency.getFirst()!=2)throw new Problem.Failure(ProblemCode.AUTHORIZATION_RULE_VIOLATION);}return requestedCountry;}
 private UUID organization(EntitlementValues.Command input,String country){
  if(input.businessReference()!=null){var rows=jdbc.query("SELECT id,status FROM commerce.organization WHERE country_code=? AND business_reference=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2)},country,input.businessReference());if(rows.size()>1)throw new Problem.Failure(ProblemCode.AUTHORIZATION_RULE_VIOLATION);if(!rows.isEmpty()){if(((String)rows.getFirst()[1]).equals("SUSPENDED"))throw EntitlementValues.transition();return (UUID)rows.getFirst()[0];}}
  UUID id=EventCodec.uuid7(clock.instant());jdbc.update("INSERT INTO commerce.organization(id,created_by,updated_by,name,business_reference,country_code,status) VALUES (?,?,?,?,?,?,'PENDING')",id,"nigson-experience","nigson-experience",input.organizationName(),input.businessReference(),country);return id;
 }
 private void activateOrganization(UUID organization,UUID party){Timestamp now=Timestamp.from(clock.instant());jdbc.update("UPDATE commerce.organization SET status='ACTIVE',version=version+1,updated_at=?,updated_by='nigson-experience' WHERE id=? AND status='PENDING'",now,organization);var members=jdbc.query("SELECT id,status,role FROM commerce.organization_membership WHERE organization_id=? AND party_id=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getString(3)},organization,party);if(members.isEmpty())jdbc.update("INSERT INTO commerce.organization_membership(id,created_by,updated_by,organization_id,party_id,role,status) VALUES (?,?,?,?,?,'OWNER','ACTIVE')",EventCodec.uuid7(clock.instant()),"nigson-experience","nigson-experience",organization,party);else{var m=members.getFirst();if(!((String)m[2]).equals("OWNER"))throw new Problem.Failure(ProblemCode.AUTHORIZATION_RULE_VIOLATION);if(!((String)m[1]).equals("ACTIVE"))jdbc.update("UPDATE commerce.organization_membership SET status='ACTIVE',version=version+1,updated_at=?,updated_by='nigson-experience' WHERE id=?",now,m[0]);}}
 private void assignBook(UUID organization,EntitlementValues.Command input,String country){if(input.priceBookId()==null)return;var rows=jdbc.query("SELECT b.currency_code FROM commerce.price_book b JOIN commerce.market m ON m.id=? WHERE b.id=? AND b.active AND b.segment='DISTRIBUTOR' AND b.currency_code=m.currency_code AND b.effective_from<=? AND (b.effective_until IS NULL OR b.effective_until>?) FOR SHARE OF b",(r,n)->r.getString(1),input.marketId(),input.priceBookId(),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));if(rows.size()!=1||!country.equals("NG"))throw new Problem.Failure(ProblemCode.AUTHORIZATION_RULE_VIOLATION);var existing=jdbc.query("SELECT id,version FROM commerce.price_assignment WHERE organization_id=? AND market_id=? FOR UPDATE",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getLong(2)},organization,input.marketId());Timestamp now=Timestamp.from(clock.instant());if(existing.isEmpty())jdbc.update("INSERT INTO commerce.price_assignment(id,created_by,updated_by,organization_id,market_id,price_book_id,active) VALUES (?,?,?,?,?,?,true)",EventCodec.uuid7(clock.instant()),"nigson-experience","nigson-experience",organization,input.marketId(),input.priceBookId());else jdbc.update("UPDATE commerce.price_assignment SET price_book_id=?,active=true,version=version+1,updated_at=?,updated_by='nigson-experience' WHERE id=?",input.priceBookId(),now,existing.getFirst()[0]);}
 private PlatformStore.Response finish(EntitlementValues.Command input,UUID organization,String state,long revision,String key,UUID correlation){
  UUID id=jdbc.queryForObject("SELECT id FROM commerce.account_entitlement WHERE source_application_id=? AND entitlement=?",UUID.class,input.applicationId(),entitlement);
  if(input.decision()==EntitlementValues.Decision.ACTIVATE)audit.append("nigson-experience",AuditValues.ActorKind.SERVICE,AuditValues.Action.ENTITLEMENT_APPROVAL,AuditValues.Resource.ENTITLEMENT,id,correlation,AuditValues.Reason.PUBLICATION_REVIEW,null,Map.of("state",state,"revision",revision,"applicationId",input.applicationId()),null);
  else audit.append("nigson-experience",AuditValues.ActorKind.SERVICE,AuditValues.Action.ENTITLEMENT_SUSPENSION,AuditValues.Resource.ENTITLEMENT,id,correlation,AuditValues.Reason.SECURITY_POLICY,Map.of("state","ACTIVE"),Map.of("state",state,"revision",revision),null);
  var env=new LinkedHashMap<String,Object>();env.put("event_id",EventCodec.uuid7(clock.instant()));env.put("event_type","distributor.entitlement.changed.v1");env.put("schema_version",1);env.put("producer","commerce");env.put("occurred_at",clock.instant());env.put("aggregate_id",organization);env.put("aggregate_version",revision);env.put("organization_id",organization);env.put("correlation_id",correlation);var span=io.opentelemetry.api.trace.Span.current().getSpanContext();env.put("traceparent",span.isValid()?"00-"+span.getTraceId()+"-"+span.getSpanId()+"-"+span.getTraceFlags().asHex():null);env.put("payload",Map.of("command_id",input.commandId(),"application_id",input.applicationId(),"organization_id",organization,"state",state,"revision",revision));commands.append(EventCodec.JSON.writeValueAsString(env),"nigson-experience");
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.authorization.entitlement","operation",input.decision().name()).increment();}});
  return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(new EntitlementValues.Acknowledgement(input.commandId(),organization,state,revision)),organization);
 }
 private PlatformStore.Response ack(UUID command,UUID org,String state,long revision){return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(new EntitlementValues.Acknowledgement(command,org,state,revision)),org);}
}
