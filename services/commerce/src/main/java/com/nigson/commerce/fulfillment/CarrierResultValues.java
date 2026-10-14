package com.nigson.commerce.fulfillment;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
/** Closed provider acknowledgement; carrier-created labels never prove physical dispatch. */
public final class CarrierResultValues {
 private CarrierResultValues(){}
 public record Result(UUID commandId,String provider,String reference,String status,Instant occurredAt,String trackingUrl,String labelObjectKey,String labelChecksum,String bookingState,String mode){
  public Result{ApiValues.uuid(commandId==null?null:commandId.toString());if(!"FEDEX".equals(provider)||reference==null||!reference.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")||!Set.of("CREATED","READY","SHIPPED","DELIVERED","CANCELLED","EXCEPTION").contains(Objects.requireNonNullElse(status,""))||occurredAt==null||!Set.of("MOCK","SANDBOX","LIVE").contains(Objects.requireNonNullElse(mode,""))||bookingState!=null&&!Set.of("BOOKED","UNKNOWN","FAILED").contains(bookingState))throw ApiValues.invalid();
   if(trackingUrl!=null){if(trackingUrl.length()>1000)throw ApiValues.invalid();FulfillmentValues.https(trackingUrl);}if(labelObjectKey!=null&&(labelObjectKey.length()>500||labelObjectKey.isBlank())||labelChecksum!=null&&!labelChecksum.matches("[a-f0-9]{64}"))throw ApiValues.invalid();
   if("BOOKED".equals(bookingState)){if(!status.equals("READY")||labelObjectKey==null||labelChecksum==null||!reference.matches("[A-Za-z0-9]{10,40}"))throw FulfillmentValues.rule();}
   else if(status.equals("READY")||labelObjectKey!=null||labelChecksum!=null)throw FulfillmentValues.rule();
   if(Set.of("UNKNOWN","FAILED").contains(Objects.requireNonNullElse(bookingState,""))&&(!Set.of("CREATED","EXCEPTION").contains(Objects.requireNonNullElse(status,""))||trackingUrl!=null))throw FulfillmentValues.rule();
  }
  @Override public String toString(){return "CarrierResult[protected]";}
 }
 private static String text(JsonNode node,String key,boolean required){var value=node.get(key);if(value==null){if(required)throw ApiValues.invalid();return null;}if(!value.isString())throw ApiValues.invalid();String raw=value.asString();if(raw.isBlank()||!raw.equals(raw.strip())||raw.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return raw;}
 public static Result result(JsonNode node){var required=Set.of("commandId","provider","reference","status","occurredAt","mode");var allowed=Set.of("commandId","provider","reference","status","occurredAt","mode","trackingUrl","labelObjectKey","labelChecksum","bookingState");if(node==null||!node.isObject()||!node.propertyNames().containsAll(required)||!allowed.containsAll(node.propertyNames()))throw ApiValues.invalid();return new Result(ApiValues.uuid(text(node,"commandId",true)),text(node,"provider",true),text(node,"reference",true),text(node,"status",true),ApiValues.instant(text(node,"occurredAt",true)),text(node,"trackingUrl",false),text(node,"labelObjectKey",false),text(node,"labelChecksum",false),text(node,"bookingState",false),text(node,"mode",true));}
}
