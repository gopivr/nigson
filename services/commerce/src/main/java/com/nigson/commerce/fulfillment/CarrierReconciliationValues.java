package com.nigson.commerce.fulfillment;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
/** Commerce-owned wire values; integration entities never cross the owner boundary. */
public final class CarrierReconciliationValues {
 private CarrierReconciliationValues(){}
 private static boolean validReason(String value){return value!=null&&!value.isBlank()&&value.length()>=10&&value.length()<=500&&!value.codePoints().anyMatch(Character::isISOControl);}
 public static void requireReason(String value){if(!validReason(value))throw ApiValues.invalid();}
 public record Request(UUID bookingRequestId,String mode,String reason){public Request{ApiValues.uuid(bookingRequestId==null?null:bookingRequestId.toString());if(!Set.of("MOCK","SANDBOX","LIVE").contains(Objects.requireNonNullElse(mode,""))||!validReason(reason))throw ApiValues.invalid();}@Override public String toString(){return "CarrierReconciliationRequest[protected]";}}
 public record Result(UUID shipmentId,String state,UUID jobId){public Result{ApiValues.uuid(shipmentId==null?null:shipmentId.toString());ApiValues.uuid(jobId==null?null:jobId.toString());if(!Set.of("BOOKED","UNKNOWN","FAILED").contains(Objects.requireNonNullElse(state,"")))throw ApiValues.invalid();}}
 public static String reason(JsonNode body){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("reason"))||!body.path("reason").isString())throw ApiValues.invalid();String value=body.path("reason").asString();requireReason(value);return value;}
 public static Result result(JsonNode body,UUID shipment){if(body==null||!body.isObject()||!body.propertyNames().equals(Set.of("shipmentId","state","jobId")))throw ApiValues.invalid();for(var value:body)if(!value.isString())throw ApiValues.invalid();var result=new Result(ApiValues.uuid(body.path("shipmentId").asString()),body.path("state").asString(),ApiValues.uuid(body.path("jobId").asString()));if(!shipment.equals(result.shipmentId()))throw FulfillmentValues.rule();return result;}
}
