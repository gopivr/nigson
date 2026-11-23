package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.JsonNode;
import com.nigson.integration.providers.PandagoAdapter;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.*;
import com.nigson.shared.problem.*;

/** Durable single-write cancellation; owner replies are replayable without repeating DELETE. */
public final class PandagoCancellationService {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final PandagoAdapter provider;
 private final Clock clock;private final UUID generation;private final Instant notBefore,approvedUntil;private final AuditWriter audit;
 public PandagoCancellationService(JdbcTemplate jdbc,PlatformTransactionManager manager,PandagoAdapter provider,Clock clock,UUID generation,Instant notBefore,Instant approvedUntil,AuditWriter audit){this.jdbc=jdbc;this.provider=provider;this.clock=clock;this.generation=generation;this.notBefore=notBefore;this.approvedUntil=approvedUntil;this.audit=audit;tx=new TransactionTemplate(manager);tx.setTimeout(4);}
 private java.sql.Timestamp stamp(Instant at){return java.sql.Timestamp.from(at);}
 private String mode(){return provider.mode()==ProviderHttp.Mode.TEST?"SANDBOX":provider.mode().name();}
 public JsonNode request(PrivateWorkload.Proof proof,JsonNode body,String key,UUID correlation){
  var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-198")).findFirst().orElseThrow();
  if(proof==null||proof.actor()!=null||!route.equals(proof.route())||!route.client().equals(proof.client())||proof.subject()==null||proof.subject().isBlank())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("cancellationId","shipmentId","bookingRequestId","reference","mode","requestedAt")))throw ApiValues.invalid();for(var v:body)if(!v.isString())throw ApiValues.invalid();
  UUID id=ApiValues.uuid(body.path("cancellationId").asString()),shipment=ApiValues.uuid(body.path("shipmentId").asString()),booking=ApiValues.uuid(body.path("bookingRequestId").asString());ApiValues.uuid(correlation==null?null:correlation.toString());
  String reference=body.path("reference").asString();Instant requested=ApiValues.instant(body.path("requestedAt").asString());if(!id.toString().equals(key)||!reference.matches("[A-Za-z0-9_-]{1,160}")||requested.isAfter(clock.instant().plusSeconds(5)))throw ApiValues.invalid();
  if(!mode().equals(body.path("mode").asString()))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);
  return tx.execute(status->{
   var bookings=jdbc.queryForList("SELECT * FROM integration.carrier_booking WHERE shipment_id=? AND booking_reference=? AND provider='PANDAGO' FOR UPDATE",shipment,booking);if(bookings.size()!=1)throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);var b=bookings.getFirst();
   if(!mode().equals(b.get("mode"))||b.get("account_hash")!=null&&!provider.accountFingerprint().equals(b.get("account_hash")))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);
   var existing=jdbc.queryForList("SELECT request_hash FROM integration.carrier_cancellation WHERE source_request_id=?",id);String hash=EventCodec.hash(body);
   if(!existing.isEmpty()){if(!hash.equals(existing.getFirst().get("request_hash")))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);return response(id);}
   if(!reference.equals(booking.toString())&&!reference.equals(b.get("provider_shipment_id")))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
   jdbc.update("INSERT INTO integration.carrier_cancellation(id,created_by,updated_by,source_request_id,booking_id,shipment_id,reference,mode,account_hash,request_hash,generation,requested_at,piece_numbers) VALUES (?,'pandago-cancellation','pandago-cancellation',?,?,?,?,?,?,?,?,?,'[]'::jsonb)",id,id,b.get("id"),shipment,reference,mode(),provider.accountFingerprint(),hash,generation,stamp(requested));
   audit.append("nigson-commerce",AuditValues.ActorKind.SERVICE,AuditValues.Action.QUEUE_REPLAY,AuditValues.Resource.SHIPMENT,shipment,correlation,AuditValues.Reason.OPERATOR_REQUEST,null,Map.of("state","QUEUED"),null);return response(id);
  });
 }
 private JsonNode response(UUID id){var row=jdbc.queryForMap("SELECT shipment_id,state,callback_body FROM integration.carrier_cancellation WHERE source_request_id=?",id);var body=EventCodec.JSON.createObjectNode();body.put("cancellationId",id.toString());body.put("shipmentId",row.get("shipment_id").toString());String state=row.get("state").toString();body.put("state",Set.of("CANCELLED","REJECTED","UNKNOWN").contains(state)?state:"QUEUED");if(row.get("callback_body")!=null)body.set("receipt",EventCodec.JSON.readTree(row.get("callback_body").toString()));return body;}
 private record Claim(UUID id,UUID booking,UUID shipment,UUID reference,long version,String sourceReference,String providerId,boolean write,boolean suppressed,boolean accountValid){}
 private Claim claim(UUID id){return tx.execute(status->{
  var ids=jdbc.query("SELECT booking_id FROM integration.carrier_cancellation WHERE id=?",(r,n)->r.getObject(1,UUID.class),id);if(ids.isEmpty())return null;
  var b=jdbc.queryForMap("SELECT * FROM integration.carrier_booking WHERE id=? FOR UPDATE",ids.getFirst());var c=jdbc.queryForMap("SELECT * FROM integration.carrier_cancellation WHERE id=? FOR UPDATE",id);if(!"PANDAGO".equals(b.get("provider"))||!mode().equals(b.get("mode"))||!mode().equals(c.get("mode")))return null;
  if(Set.of("CANCELLED","REJECTED").contains(c.get("state"))||c.get("lease_until")!=null&&((java.sql.Timestamp)c.get("lease_until")).toInstant().isAfter(clock.instant()))return null;
  boolean fresh=c.get("submitted_at")==null&&Set.of("QUEUED","UNKNOWN").contains(c.get("state"))&&generation.equals(c.get("generation"))&&!((java.sql.Timestamp)c.get("requested_at")).toInstant().isBefore(notBefore)&&approvedUntil.isAfter(clock.instant());
  boolean suppressed=fresh&&b.get("prepared_at")==null&&b.get("provider_shipment_id")==null&&Set.of("PENDING","CLAIMED","FAILED").contains(b.get("state"));
  if(suppressed)jdbc.update("UPDATE integration.carrier_booking SET state='FAILED',lease_until=NULL,next_poll_at=NULL,version=version+1,error_code='CARRIER_CANCELLED' WHERE id=?",b.get("id"));
  boolean write=fresh&&!suppressed&&b.get("provider_shipment_id")!=null&&provider.accountFingerprint().equals(c.get("account_hash"))&&provider.accountFingerprint().equals(b.get("account_hash"));
  jdbc.update("UPDATE integration.carrier_cancellation SET state=?,submitted_at=CASE WHEN ? THEN COALESCE(submitted_at,?) ELSE submitted_at END,lease_until=?,attempt_count=attempt_count+1,version=version+1,updated_at=? WHERE id=?",write?"SUBMITTING":"UNKNOWN",write,stamp(clock.instant()),stamp(clock.instant().plusSeconds(120)),stamp(clock.instant()),id);
  return new Claim(id,ids.getFirst(),(UUID)c.get("shipment_id"),(UUID)b.get("booking_reference"),((Number)c.get("version")).longValue()+1,c.get("reference").toString(),(String)b.get("provider_shipment_id"),write,suppressed,provider.accountFingerprint().equals(c.get("account_hash"))&&provider.accountFingerprint().equals(b.get("account_hash"))&&mode().equals(b.get("mode")));
 });}
 private void save(Claim c,String state,String reason){tx.executeWithoutResult(status->{
  var body=EventCodec.JSON.createObjectNode();body.put("commandId",UUID.nameUUIDFromBytes((c.id()+":"+state).getBytes(java.nio.charset.StandardCharsets.US_ASCII)).toString());body.put("cancellationId",c.id().toString());body.put("shipmentId",c.shipment().toString());body.put("bookingRequestId",c.reference().toString());body.put("reference",c.sourceReference());body.put("mode",mode());body.put("state",state);body.put("occurredAt",clock.instant().toString());body.put("reasonCode",reason);
  // Keep the exact original receipt for repeated UNKNOWN observations too.
  int changed=jdbc.update("UPDATE integration.carrier_cancellation SET state=?,outcome_state=?,callback_body=CASE WHEN outcome_state=? THEN callback_body ELSE ?::jsonb END,lease_until=NULL,next_attempt_at=?,error_code=?,version=version+1,updated_at=? WHERE id=? AND version=? AND lease_until>?",state,state,state,EventCodec.JSON.writeValueAsString(body),stamp(clock.instant().plusSeconds(30)),reason.equals("NONE")?null:reason,stamp(clock.instant()),c.id(),c.version(),stamp(clock.instant()));
  if(changed==1)audit.append("pandago-cancellation",AuditValues.ActorKind.SYSTEM,AuditValues.Action.SHIPMENT_PROVIDER_RESULT,AuditValues.Resource.SHIPMENT,c.shipment(),c.id(),AuditValues.Reason.RECONCILIATION,null,Map.of("state",state),null);
 });}
 @Scheduled(fixedDelayString="${NIGSON_CARRIER_CANCELLATION_DELAY_MS:30000}")public void scheduled(){try{poll();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("PANDAGO_CANCELLATION_RECOVERY_REQUIRED");}}
 public int poll(){var ids=jdbc.query("SELECT id FROM integration.carrier_cancellation WHERE booking_id IN(SELECT id FROM integration.carrier_booking WHERE provider='PANDAGO') AND state IN ('QUEUED','SUBMITTING','UNKNOWN') AND next_attempt_at<=? AND (lease_until IS NULL OR lease_until<=?) ORDER BY next_attempt_at,id LIMIT 20",(r,n)->r.getObject(1,UUID.class),stamp(clock.instant()),stamp(clock.instant()));for(UUID id:ids){try{process(id);}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("PANDAGO_CANCELLATION_REQUIRES_REVIEW");}}return ids.size();}
 public void process(UUID id){Claim c=claim(id);if(c==null)return;
  if(c.suppressed()){save(c,"CANCELLED","NONE");return;}
  if(c.providerId()==null||!c.accountValid()){save(c,"UNKNOWN","PROVIDER_OUTCOME_UNKNOWN");return;}
  try{
   boolean permission=c.write()&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM integration.carrier_cancellation WHERE id=? AND version=? AND state='SUBMITTING' AND generation=? AND lease_until>?)",Boolean.class,c.id(),c.version(),generation,stamp(clock.instant().plusSeconds(20))));
   if(permission){
    try{if(provider.cancel(c.providerId(),"REASON_UNKNOWN")==PandagoAdapter.Cancellation.CANCELLED){save(c,"CANCELLED","NONE");return;}}
    catch(ProviderHttp.Failure failure){if(!failure.uncertain&&failure.status==409)save(c,"REJECTED","CARRIER_CANCEL_REJECTED");else save(c,"UNKNOWN","PROVIDER_OUTCOME_UNKNOWN");return;}
   }
   var order=provider.order(c.providerId());if(!order.id().equals(c.providerId())||!order.clientOrderId().equals(c.reference().toString()))throw new IllegalArgumentException("CARRIER_IDENTITY_MISMATCH");
   if(order.status().equals("CANCELLED"))save(c,"CANCELLED","NONE");else save(c,"UNKNOWN","PROVIDER_OUTCOME_UNKNOWN");
  }catch(ProviderHttp.Failure failure){save(c,"UNKNOWN","PROVIDER_OUTCOME_UNKNOWN");}
  catch(RuntimeException unavailable){save(c,"UNKNOWN","PROVIDER_OUTCOME_UNKNOWN");}
 }
}
