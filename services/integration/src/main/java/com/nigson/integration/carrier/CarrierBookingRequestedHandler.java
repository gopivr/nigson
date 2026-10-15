package com.nigson.integration.carrier;

import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import com.nigson.integration.platform.*;
import com.nigson.integration.provider.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ProviderHttp;

/** EVT-027 intake only. Intent, job, audit and the caller's inbox receipt share one owner transaction. */
public final class CarrierBookingRequestedHandler implements EventHandler {
 private final JobRepository jobs;private final AuditWriter audit;private final Clock clock;private final String mode;private final UUID generation;private final String accountHash;
 public CarrierBookingRequestedHandler(JobRepository jobs,AuditWriter audit,Clock clock,ProviderHttp.Mode mode,UUID generation,String accountHash){
  this.jobs=Objects.requireNonNull(jobs);this.audit=Objects.requireNonNull(audit);this.clock=Objects.requireNonNull(clock);
  if(generation==null||generation.equals(new UUID(0,0)))throw new IllegalArgumentException("CARRIER_CREATION_GENERATION_REQUIRED");this.generation=generation;
  if(accountHash==null||!accountHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("CARRIER_ACCOUNT_IDENTITY_REQUIRED");this.accountHash=accountHash;
  this.mode=switch(Objects.requireNonNull(mode)){case MOCK->"MOCK";case TEST->"SANDBOX";case LIVE->"LIVE";};
 }
 public String eventType(){return "shipment.booking.requested.v1";}
 public String handle(JdbcTemplate jdbc,JsonNode event){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
  // Keep the registered closed envelope validation even when invoked outside EventPump.
  EventCodec.decode(EventCodec.JSON.writeValueAsString(event));
  if(!eventType().equals(event.path("event_type").asString())||!"commerce".equals(event.path("producer").asString()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
  var payload=event.path("payload");UUID shipment=ApiValues.uuid(payload.path("shipment_id").asString()),reference=ApiValues.uuid(payload.path("booking_request_id").asString());
  if(!shipment.equals(ApiValues.uuid(event.path("aggregate_id").asString())))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
  UUID correlation=ApiValues.uuid(event.path("correlation_id").asString());String key="carrier-booking:"+reference;
  jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
  int inserted=jdbc.update("INSERT INTO integration.carrier_booking(id,created_by,updated_by,shipment_id,business_key,provider,mode,state,request_hash) VALUES (?,'carrier-event-worker','carrier-event-worker',?,?,'FEDEX',?,'PENDING',?) ON CONFLICT DO NOTHING",EventCodec.uuid7(clock.instant()),shipment,key,mode,EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("shipment_id",shipment,"booking_request_id",reference,"provider","FEDEX"))));
  var rows=jdbc.queryForList("SELECT id,business_key FROM integration.carrier_booking WHERE shipment_id=? AND provider='FEDEX' FOR UPDATE",shipment);
  // Both uniqueness constraints are intentional: neither a new reference for this shipment nor a
  // reused reference for another shipment can manufacture a second execution.
  if(rows.size()!=1||!key.equals(rows.getFirst().get("business_key")))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);
  if(inserted==0&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM integration.integration_job WHERE business_key=?)",Boolean.class,key)))throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
  var saved=jobs.enqueueTracked(JobValues.Kind.LOGISTICS_CREATE,key,"commerce",shipment);
  if(inserted==1&&!saved.created())throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
  if(saved.created())jdbc.update("UPDATE integration.integration_job SET cursor=?::jsonb WHERE id=?",EventCodec.JSON.writeValueAsString(Map.of("v",1,"stage","INTENT","generation",generation.toString(),"correlationId",correlation.toString(),"accountHash",accountHash,"sourceOccurredAt",event.path("occurred_at").asString())),saved.job().id());
  if(inserted==1)audit.append("integration-event-worker",AuditValues.ActorKind.SYSTEM,AuditValues.Action.QUEUE_REPLAY,AuditValues.Resource.QUEUE,saved.job().id(),correlation,AuditValues.Reason.RECONCILIATION,null,Map.of("state","PENDING","version",saved.job().version()),null);
  // A semantic replay preserves the saved provider mode, request hash, state, lease and job outcome.
  // This receipt acknowledges durable work, never a provider booking or label/dispatch success.
  return "APPLIED";
 }
}
