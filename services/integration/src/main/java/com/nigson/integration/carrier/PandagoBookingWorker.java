package com.nigson.integration.carrier;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.JsonNode;
import com.nigson.integration.providers.*;
import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Persist prepare before HTTP. A crash after prepare can only reconcile, never create again. */
public final class PandagoBookingWorker {
 private record Claim(UUID id,UUID shipment,UUID reference,UUID generation,long version,String prior,Instant prepared,String account,String providerId,JsonNode result,Instant source){}
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final PandagoAdapter provider;private final GeocodingProvider geocoder;private final PandagoCommerceClient commerce;private final Clock clock;private final UUID generation;private final Instant notBefore,approvedUntil;private final io.micrometer.core.instrument.MeterRegistry metrics;
 public PandagoBookingWorker(JdbcTemplate jdbc,PlatformTransactionManager manager,PandagoAdapter provider,GeocodingProvider geocoder,PandagoCommerceClient commerce,Clock clock,UUID generation,Instant notBefore,Instant approvedUntil,io.micrometer.core.instrument.MeterRegistry metrics){this.jdbc=jdbc;this.provider=provider;this.geocoder=geocoder;this.commerce=commerce;this.clock=clock;this.generation=generation;this.notBefore=notBefore;this.approvedUntil=approvedUntil;this.metrics=metrics;tx=new TransactionTemplate(manager);tx.setTimeout(5);}
 private java.sql.Timestamp stamp(Instant at){return java.sql.Timestamp.from(at);}
 private Claim claim(){return tx.execute(status->{
  jdbc.execute("SET LOCAL lock_timeout='350ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
  jdbc.update("UPDATE integration.carrier_booking SET state=CASE WHEN prepared_at IS NULL THEN 'PENDING' ELSE 'UNKNOWN' END,lease_until=NULL,next_poll_at=?,version=version+1,error_code='PROVIDER_OUTCOME_UNKNOWN' WHERE provider='PANDAGO' AND booking_reference IS NOT NULL AND state='CLAIMED' AND lease_until<=?",stamp(clock.instant()),stamp(clock.instant()));
  var ids=jdbc.query("SELECT id FROM integration.carrier_booking WHERE provider='PANDAGO' AND booking_reference IS NOT NULL AND state IN ('PENDING','UNKNOWN','BOOKED','FAILED') AND next_poll_at<=? ORDER BY next_poll_at,id FOR UPDATE SKIP LOCKED LIMIT 1",(r,n)->r.getObject(1,UUID.class),stamp(clock.instant()));if(ids.isEmpty())return null;
  var row=jdbc.queryForMap("SELECT * FROM integration.carrier_booking WHERE id=?",ids.getFirst());long version=((Number)row.get("version")).longValue()+1;
  jdbc.update("UPDATE integration.carrier_booking SET state='CLAIMED',lease_until=?,version=?,updated_at=?,updated_by='pandago-worker' WHERE id=?",stamp(clock.instant().plusSeconds(120)),version,stamp(clock.instant()),ids.getFirst());
  return new Claim(ids.getFirst(),(UUID)row.get("shipment_id"),(UUID)row.get("booking_reference"),(UUID)row.get("creation_generation"),version,row.get("state").toString(),row.get("prepared_at")==null?null:((java.sql.Timestamp)row.get("prepared_at")).toInstant(),(String)row.get("account_hash"),(String)row.get("provider_shipment_id"),row.get("result_body")==null?null:EventCodec.JSON.readTree(row.get("result_body").toString()),((java.sql.Timestamp)row.get("source_occurred_at")).toInstant());
 });}
 private String mode(){return provider.mode()==ProviderHttp.Mode.TEST?"SANDBOX":provider.mode().name();}
 private void approval(){if(!approvedUntil.isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);}
 private JsonNode result(Claim claim,String state,String reference){var body=EventCodec.JSON.createObjectNode();String id=claim.reference()+":"+state+":"+reference;body.put("commandId",UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.US_ASCII)).toString());body.put("provider","PANDAGO");body.put("reference",reference);body.put("status",state.equals("BOOKED")?"READY":"EXCEPTION");body.put("bookingState",state);body.put("occurredAt",clock.instant().toString());body.put("mode",mode());return body;}
 private boolean save(Claim claim,String state,String providerId,String providerStatus,JsonNode result){return Boolean.TRUE.equals(tx.execute(status->{int changed=jdbc.update("UPDATE integration.carrier_booking SET state=?,provider_shipment_id=coalesce(?,provider_shipment_id),last_provider_status=coalesce(?,last_provider_status),result_body=?::jsonb,lease_until=NULL,next_poll_at=?,error_code=?,version=version+1,updated_at=?,updated_by='pandago-worker' WHERE id=? AND version=? AND state='CLAIMED'",state,providerId,providerStatus,EventCodec.JSON.writeValueAsString(result),stamp(clock.instant().plusSeconds(30)),state.equals("UNKNOWN")?"PROVIDER_OUTCOME_UNKNOWN":state.equals("FAILED")?"PROVIDER_RULE_VIOLATION":null,stamp(clock.instant()),claim.id(),claim.version());if(changed==1)new com.nigson.integration.platform.AuditWriter(jdbc,clock,metrics).append("pandago-worker",AuditValues.ActorKind.SYSTEM,AuditValues.Action.SHIPMENT_PROVIDER_RESULT,AuditValues.Resource.SHIPMENT,claim.shipment(),claim.reference(),AuditValues.Reason.RECONCILIATION,Map.of("version",claim.version()),Map.of("version",claim.version()+1),null);return changed==1;}));}
 private void push(Claim claim,JsonNode body){commerce.acknowledge(claim.shipment(),body);tx.executeWithoutResult(status->jdbc.update("UPDATE integration.carrier_booking SET next_poll_at=NULL,updated_at=?,updated_by='pandago-worker' WHERE id=? AND result_body=?::jsonb AND state IN ('BOOKED','UNKNOWN','FAILED')",stamp(clock.instant()),claim.id(),EventCodec.JSON.writeValueAsString(body)));}
 private void defer(Claim claim){tx.executeWithoutResult(status->jdbc.update("UPDATE integration.carrier_booking SET state=?,lease_until=NULL,next_poll_at=?,version=version+1,error_code='PROVIDER_UNAVAILABLE' WHERE id=? AND version=? AND state='CLAIMED'",claim.prepared()==null&&claim.prior().equals("PENDING")?"PENDING":"UNKNOWN",stamp(clock.instant().plusSeconds(30)),claim.id(),claim.version()));}
 @Scheduled(fixedDelayString="${NIGSON_PANDAGO_BOOKING_DELAY_MS:1000}")public void scheduled(){try{poll();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("PANDAGO_BOOKING_RECOVERY_REQUIRED");}}
 public int poll(){Claim claim=claim();if(claim==null)return 0;boolean prepared=claim.prepared()!=null;try{
  if(claim.result()!=null){if(save(claim,claim.result().path("bookingState").asString(),claim.providerId(),null,claim.result()))push(claim,claim.result());return 1;}
  approval();if(prepared||!"PENDING".equals(claim.prior())||!generation.equals(claim.generation())||claim.source().isBefore(notBefore)){
   // No known PandaGo ID means manual reference reconciliation, never a second POST.
   if(claim.providerId()==null){var body=result(claim,"UNKNOWN",claim.reference().toString());if(save(claim,"UNKNOWN",null,null,body))push(claim,body);return 1;}
   if(!provider.accountFingerprint().equals(claim.account()))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);
   var order=provider.order(claim.providerId());if(!order.clientOrderId().equals(claim.reference().toString()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
   var body=result(claim,"BOOKED",order.id());if(save(claim,"BOOKED",order.id(),order.status(),body))push(claim,body);return 1;
  }
  JsonNode context=commerce.context(claim.shipment());if(!claim.shipment().toString().equals(context.path("shipmentId").asString())||!claim.reference().toString().equals(context.path("bookingReference").asString())||!provider.accountFingerprint().equals(context.path("accountHash").asString())||!mode().equals(context.path("mode").asString()))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);
  var a=context.path("address");var point=geocoder.resolve(new GeocodingProvider.Address(a.path("line1").asString(),a.path("line2").isString()?a.path("line2").asString():null,a.path("city").asString(),a.path("region").asString(),a.path("postalCode").isString()?a.path("postalCode").asString():null,a.path("countryCode").asString()));
  var location=new PandagoAdapter.Location(String.join(", ",a.path("line1").asString(),a.path("city").asString(),a.path("region").asString(),a.path("countryCode").asString()),point.latitude(),point.longitude());
  Instant scheduled=context.path("preorderedFor").isString()?Instant.parse(context.path("preorderedFor").asString()):null;String method=context.path("paymentMethod").asString();var amount=new BigDecimal(context.path("amount").asString());String description=context.path("description").asString();
  var recipient=new PandagoAdapter.Recipient(a.path("recipient").asString(),a.path("phone").asString(),location);var quote=provider.quote(location,scheduled);
  if(quote.fee().compareTo(new BigDecimal(context.path("deliveryAmount").asString()))!=0)throw new Problem.Failure(ProblemCode.CHECKOUT_REPRICE_REQUIRED);
  approval();boolean permission=Boolean.TRUE.equals(tx.execute(status->jdbc.update("UPDATE integration.carrier_booking SET prepared_at=?,account_hash=?,request_hash=?,updated_at=? WHERE id=? AND state='CLAIMED' AND version=? AND prepared_at IS NULL AND creation_generation=? AND lease_until>? AND NOT EXISTS(SELECT 1 FROM integration.carrier_cancellation c WHERE c.booking_id=integration.carrier_booking.id AND c.state<>'REJECTED')",stamp(clock.instant()),provider.accountFingerprint(),EventCodec.hash(context),stamp(clock.instant()),claim.id(),claim.version(),generation,stamp(clock.instant().plusSeconds(20)))==1));
  if(!permission)return 1;prepared=true;
  var order=provider.confirm(quote,claim.reference(),recipient,amount,description,method,scheduled);
  var body=result(claim,"BOOKED",order.id());if(save(claim,"BOOKED",order.id(),order.status(),body)){metrics.counter("nigson.pandago.bookings","state","BOOKED").increment();push(claim,body);}
 }catch(RuntimeException failure){
  if(!prepared&&(failure instanceof IllegalArgumentException||failure instanceof Problem.Failure p&&Set.of(ProblemCode.CHECKOUT_REPRICE_REQUIRED,ProblemCode.PROVIDER_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH).contains(p.code))){var body=result(claim,"FAILED",claim.reference().toString());if(save(claim,"FAILED",null,null,body)){try{push(claim,body);}catch(RuntimeException unavailable){}}}
  else if(prepared){var body=result(claim,"UNKNOWN",claim.reference().toString());if(save(claim,"UNKNOWN",null,null,body)){metrics.counter("nigson.pandago.bookings","state","UNKNOWN").increment();try{push(claim,body);}catch(RuntimeException unavailable){}}}
  else defer(claim);
 }return 1;}
}
