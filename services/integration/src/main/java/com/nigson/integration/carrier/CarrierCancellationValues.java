package com.nigson.integration.carrier;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
public final class CarrierCancellationValues {
 private CarrierCancellationValues(){}
 public static final Set<String> REASONS=Set.of("NONE","PROVIDER_OUTCOME_UNKNOWN","PROVIDER_UNAVAILABLE","PROVIDER_MODE_MISMATCH","CARRIER_CANCEL_REJECTED","CARRIER_ALREADY_DELIVERED","CARRIER_APPROVAL_REQUIRED");
 public record Command(UUID cancellationId,UUID shipmentId,UUID bookingRequestId,String reference,String mode,Instant requestedAt){public Command{uuid(cancellationId);uuid(shipmentId);uuid(bookingRequestId);identity(reference,mode);if(requestedAt==null)throw ApiValues.invalid();}@Override public String toString(){return "CarrierCancellationCommand[protected]";}}
 public record Receipt(UUID commandId,UUID cancellationId,UUID shipmentId,UUID bookingRequestId,String reference,String mode,String state,Instant occurredAt,String reasonCode){public Receipt{uuid(commandId);uuid(cancellationId);uuid(shipmentId);uuid(bookingRequestId);identity(reference,mode);if(!Set.of("UNKNOWN","CANCELLED","REJECTED").contains(state)||occurredAt==null||!REASONS.contains(reasonCode)||state.equals("CANCELLED")&&!reasonCode.equals("NONE"))throw ApiValues.invalid();}@Override public String toString(){return "CarrierCancellationReceipt[protected]";}}
 public record Acknowledgement(UUID cancellationId,UUID shipmentId,String state,Receipt receipt){}
 private static void uuid(UUID id){ApiValues.uuid(id==null?null:id.toString());}
 private static void identity(String reference,String mode){if(reference==null||!reference.matches("[A-Za-z0-9]{10,40}")||!Set.of("MOCK","SANDBOX","LIVE").contains(Objects.requireNonNullElse(mode,"")))throw ApiValues.invalid();}
 public static Command command(JsonNode body){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("cancellationId","shipmentId","bookingRequestId","reference","mode","requestedAt")))throw ApiValues.invalid();for(var value:body)if(!value.isString())throw ApiValues.invalid();return new Command(ApiValues.uuid(body.path("cancellationId").asString()),ApiValues.uuid(body.path("shipmentId").asString()),ApiValues.uuid(body.path("bookingRequestId").asString()),body.path("reference").asString(),body.path("mode").asString(),ApiValues.instant(body.path("requestedAt").asString()));}
 public static Receipt receipt(JsonNode body){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("commandId","cancellationId","shipmentId","bookingRequestId","reference","mode","state","occurredAt","reasonCode")))throw ApiValues.invalid();for(var value:body)if(!value.isString())throw ApiValues.invalid();return new Receipt(ApiValues.uuid(body.path("commandId").asString()),ApiValues.uuid(body.path("cancellationId").asString()),ApiValues.uuid(body.path("shipmentId").asString()),ApiValues.uuid(body.path("bookingRequestId").asString()),body.path("reference").asString(),body.path("mode").asString(),body.path("state").asString(),ApiValues.instant(body.path("occurredAt").asString()),body.path("reasonCode").asString());}
 public static String hash(Command input){return EventCodec.hash(EventCodec.JSON.valueToTree(input));}
}
