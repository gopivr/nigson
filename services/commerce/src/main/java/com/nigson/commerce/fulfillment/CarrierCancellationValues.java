package com.nigson.commerce.fulfillment;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
public final class CarrierCancellationValues {
 private CarrierCancellationValues(){}
 public static final Set<String> STATES=Set.of("REQUESTED","QUEUED","UNKNOWN","CANCELLED","REJECTED");
 public static final Set<String> REASONS=Set.of("NONE","PROVIDER_OUTCOME_UNKNOWN","PROVIDER_UNAVAILABLE","PROVIDER_MODE_MISMATCH","CARRIER_CANCEL_REJECTED","CARRIER_ALREADY_DELIVERED","CARRIER_APPROVAL_REQUIRED");
 public record View(UUID id,UUID shipmentId,String state,long version,long shipmentVersion,String errorCode){public View{uuid(id);uuid(shipmentId);if(!STATES.contains(state)||version<0||shipmentVersion<0||errorCode!=null&&!REASONS.contains(errorCode))throw ApiValues.invalid();}}
 public record Command(UUID cancellationId,UUID shipmentId,UUID bookingRequestId,String reference,String mode,Instant requestedAt){public Command{uuid(cancellationId);uuid(shipmentId);uuid(bookingRequestId);identity(reference,mode);if(requestedAt==null)throw ApiValues.invalid();}@Override public String toString(){return "CarrierCancellationCommand[protected]";}}
 public record Receipt(UUID commandId,UUID cancellationId,UUID shipmentId,UUID bookingRequestId,String reference,String mode,String state,Instant occurredAt,String reasonCode){public Receipt{uuid(commandId);uuid(cancellationId);uuid(shipmentId);uuid(bookingRequestId);identity(reference,mode);if(!Set.of("UNKNOWN","CANCELLED","REJECTED").contains(state)||occurredAt==null||!REASONS.contains(reasonCode)||state.equals("CANCELLED")&&!reasonCode.equals("NONE"))throw ApiValues.invalid();}@Override public String toString(){return "CarrierCancellationReceipt[protected]";}}
 public record Acknowledgement(UUID cancellationId,UUID shipmentId,String state,Receipt receipt){public Acknowledgement{uuid(cancellationId);uuid(shipmentId);if(!Set.of("QUEUED","UNKNOWN","CANCELLED","REJECTED").contains(state))throw ApiValues.invalid();if(receipt!=null&&(!cancellationId.equals(receipt.cancellationId())||!shipmentId.equals(receipt.shipmentId())))throw ApiValues.invalid();}}
 private static void uuid(UUID id){ApiValues.uuid(id==null?null:id.toString());}
 private static void identity(String reference,String mode){if(reference==null||!reference.matches("[A-Za-z0-9]{10,40}")||!Set.of("MOCK","SANDBOX","LIVE").contains(Objects.requireNonNullElse(mode,"")))throw ApiValues.invalid();}
 public static Receipt receipt(JsonNode body){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("commandId","cancellationId","shipmentId","bookingRequestId","reference","mode","state","occurredAt","reasonCode")))throw ApiValues.invalid();for(var value:body)if(!value.isString())throw ApiValues.invalid();return new Receipt(ApiValues.uuid(body.path("commandId").asString()),ApiValues.uuid(body.path("cancellationId").asString()),ApiValues.uuid(body.path("shipmentId").asString()),ApiValues.uuid(body.path("bookingRequestId").asString()),body.path("reference").asString(),body.path("mode").asString(),body.path("state").asString(),ApiValues.instant(body.path("occurredAt").asString()),body.path("reasonCode").asString());}
 public static String hash(Command input){return EventCodec.hash(EventCodec.JSON.valueToTree(input));}
 public static String safeError(Object value){return value!=null&&REASONS.contains(value.toString())?value.toString():value==null?null:"PROVIDER_OUTCOME_UNKNOWN";}
}
