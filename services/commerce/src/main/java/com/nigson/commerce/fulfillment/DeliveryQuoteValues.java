package com.nigson.commerce.fulfillment;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.commerce.pricing.CarrierPacking;

public final class DeliveryQuoteValues {
 private DeliveryQuoteValues(){}
 public record Input(UUID addressId,String serviceCode,String deliveryMode,Instant scheduledFor){
  public Input(UUID addressId,String serviceCode){this(addressId,serviceCode,"IMMEDIATE",null);}
  public Input{if(addressId==null||addressId.equals(new UUID(0,0))||serviceCode!=null&&!serviceCode.matches("[A-Z][A-Z0-9_]{0,79}"))throw ApiValues.invalid();if(deliveryMode==null)deliveryMode="IMMEDIATE";if(!Set.of("IMMEDIATE","SCHEDULED").contains(deliveryMode)||("IMMEDIATE".equals(deliveryMode))!=(scheduledFor==null))throw ApiValues.invalid();}
 }
 public record QuoteInput(UUID revisionId,UUID addressId,String serviceCode,String deliveryMode,Instant scheduledFor){
  public QuoteInput(UUID revisionId,UUID addressId,String serviceCode){this(revisionId,addressId,serviceCode,"IMMEDIATE",null);}
  public QuoteInput{if(revisionId==null||revisionId.equals(new UUID(0,0))||addressId==null||addressId.equals(new UUID(0,0))||serviceCode!=null&&!serviceCode.matches("[A-Z][A-Z0-9_]{0,79}"))throw ApiValues.invalid();if(deliveryMode==null)deliveryMode="IMMEDIATE";if(!Set.of("IMMEDIATE","SCHEDULED").contains(deliveryMode)||("IMMEDIATE".equals(deliveryMode))!=(scheduledFor==null))throw ApiValues.invalid();}
 }
 public record Quote(UUID id,String provider,String serviceCode,String currency,String deliveryAmount,Instant expiresAt,long cartVersion,String mode,
                     String estimatedDeliveryAmount,Instant estimatedPickupTime,Instant estimatedDeliveryTime,
                     String deliveryDuration,String lowerBound,String upperBound,String deliveryMode,Instant scheduledFor){
  public Quote(UUID id,String provider,String serviceCode,String currency,String deliveryAmount,Instant expiresAt,long cartVersion,String mode){this(id,provider,serviceCode,currency,deliveryAmount,expiresAt,cartVersion,mode,null,null,null,null,null,null,"IMMEDIATE",null);}
  public Quote(UUID id,String provider,String serviceCode,String currency,String deliveryAmount,Instant expiresAt,long cartVersion,String mode,
               String estimatedDeliveryAmount,Instant estimatedPickupTime,Instant estimatedDeliveryTime,
               String deliveryDuration,String lowerBound,String upperBound){this(id,provider,serviceCode,currency,deliveryAmount,expiresAt,cartVersion,mode,estimatedDeliveryAmount,estimatedPickupTime,estimatedDeliveryTime,deliveryDuration,lowerBound,upperBound,"IMMEDIATE",null);}
 }
 private static String text(JsonNode node,String name){var value=node.get(name);if(value==null||!value.isString())throw ApiValues.invalid();return value.asString();}
 private static Instant instant(JsonNode node,String name){var value=node.get(name);return value==null||value.isNull()?null:parseInstant(text(node,name));}
 private static String deliveryMode(JsonNode node){var value=node.get("deliveryMode");return value==null||value.isNull()?"IMMEDIATE":text(node,"deliveryMode");}
 public static Input input(JsonNode node){if(node==null||!node.isObject()||!node.has("addressId")||!Set.of("addressId","serviceCode","deliveryMode","scheduledFor").containsAll(node.propertyNames()))throw ApiValues.invalid();return new Input(ApiValues.uuid(text(node,"addressId")),node.has("serviceCode")&&!node.get("serviceCode").isNull()?text(node,"serviceCode"):null,deliveryMode(node),instant(node,"scheduledFor"));}
 public static QuoteInput quoteInput(JsonNode node){if(node==null||!node.isObject()||!node.propertyNames().containsAll(Set.of("revisionId","addressId"))||!Set.of("revisionId","addressId","serviceCode","deliveryMode","scheduledFor").containsAll(node.propertyNames()))throw ApiValues.invalid();return new QuoteInput(ApiValues.uuid(text(node,"revisionId")),ApiValues.uuid(text(node,"addressId")),node.has("serviceCode")&&!node.get("serviceCode").isNull()?text(node,"serviceCode"):null,deliveryMode(node),instant(node,"scheduledFor"));}
 public static CarrierPacking.Address origin(JsonNode node){var fields=Set.of("recipient","phone","line1","line2","city","region","postalCode","countryCode");if(node==null||!node.isObject()||!node.propertyNames().equals(fields))throw ApiValues.invalid();return new CarrierPacking.Address(text(node,"recipient"),text(node,"phone"),text(node,"line1"),node.path("line2").isNull()?null:text(node,"line2"),text(node,"city"),text(node,"region"),node.path("postalCode").isNull()?null:text(node,"postalCode"),text(node,"countryCode"));}
 private static Instant parseInstant(String value){try{return Instant.parse(value);}catch(java.time.format.DateTimeParseException invalid){throw ApiValues.invalid();}}

}
