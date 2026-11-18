package com.nigson.integration.carrier;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.integration.platform.EventHandler;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import tools.jackson.databind.JsonNode;
/** Inbox and stable booking intake commit together, without provider HTTP or customer data. */
public final class PandagoBookingRequested implements EventHandler {
 private final UUID generation;private final Instant notBefore;private final Clock clock;private final String mode;
 public PandagoBookingRequested(UUID generation,Instant notBefore,Clock clock,String mode){if(generation.equals(new UUID(0,0))||!Set.of("MOCK","SANDBOX","LIVE").contains(mode))throw new IllegalArgumentException("CARRIER_GENERATION_REQUIRED");this.generation=generation;this.notBefore=notBefore;this.clock=clock;this.mode=mode;}
 public String eventType(){return "shipment.booking.requested.v1";}
 public String handle(JdbcTemplate jdbc,JsonNode event){
  var p=event.path("payload");if(!"commerce".equals(event.path("producer").asString())||!eventType().equals(event.path("event_type").asString())||!p.isObject()||!p.propertyNames().equals(Set.of("shipment_id","booking_request_id","provider"))||!"PANDAGO".equals(p.path("provider").asString()))throw ApiValues.invalid();
  UUID shipment=ApiValues.uuid(p.path("shipment_id").asString()),reference=ApiValues.uuid(p.path("booking_request_id").asString());if(!shipment.toString().equals(event.path("aggregate_id").asString()))throw ApiValues.invalid();Instant source=ApiValues.instant(event.path("occurred_at").asString());if(source.isAfter(clock.instant().plusSeconds(5)))throw ApiValues.invalid();
  String key="pandago:"+reference;boolean historical=source.isBefore(notBefore);
  jdbc.update("INSERT INTO integration.carrier_booking(id,created_by,updated_by,shipment_id,business_key,provider,mode,state,request_hash,next_poll_at,booking_reference,creation_generation,source_occurred_at,error_code) VALUES (?,'pandago-intake','pandago-intake',?,?,'PANDAGO',?,?,?,now(),?,?,?,?) ON CONFLICT(shipment_id,provider) DO NOTHING",EventCodec.uuid7(clock.instant()),shipment,key,mode,historical?"UNKNOWN":"PENDING",EventCodec.hash(p),reference,generation,java.sql.Timestamp.from(source),historical?"PROVIDER_OUTCOME_UNKNOWN":null);
  var saved=jdbc.queryForMap("SELECT business_key,booking_reference FROM integration.carrier_booking WHERE shipment_id=? AND provider='PANDAGO'",shipment);if(!key.equals(saved.get("business_key"))||!reference.equals(saved.get("booking_reference")))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);return "APPLIED";
 }
}
