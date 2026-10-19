package com.nigson.commerce.fulfillment;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.commerce.pricing.CarrierPacking;

public final class DeliveryQuoteValues {
 private DeliveryQuoteValues(){}
 public record Input(UUID addressId,String serviceCode){public Input{if(addressId==null||addressId.equals(new UUID(0,0))||serviceCode!=null&&!serviceCode.matches("[A-Z][A-Z0-9_]{0,79}"))throw ApiValues.invalid();}}
 public record QuoteInput(UUID revisionId,UUID addressId,String serviceCode){public QuoteInput{if(revisionId==null||revisionId.equals(new UUID(0,0))||addressId==null||addressId.equals(new UUID(0,0))||serviceCode!=null&&!serviceCode.matches("[A-Z][A-Z0-9_]{0,79}"))throw ApiValues.invalid();}}
 public record Quote(UUID id,String provider,String serviceCode,String currency,String deliveryAmount,Instant expiresAt,long cartVersion,String mode){}
 private static String text(JsonNode node,String name){var value=node.get(name);if(value==null||!value.isString())throw ApiValues.invalid();return value.asString();}
 public static Input input(JsonNode node){if(node==null||!node.isObject()||!node.has("addressId")||!Set.of("addressId","serviceCode").containsAll(node.propertyNames()))throw ApiValues.invalid();return new Input(ApiValues.uuid(text(node,"addressId")),node.has("serviceCode")?text(node,"serviceCode"):null);}
 public static QuoteInput quoteInput(JsonNode node){if(node==null||!node.isObject()||!node.propertyNames().containsAll(Set.of("revisionId","addressId"))||!Set.of("revisionId","addressId","serviceCode").containsAll(node.propertyNames()))throw ApiValues.invalid();return new QuoteInput(ApiValues.uuid(text(node,"revisionId")),ApiValues.uuid(text(node,"addressId")),node.has("serviceCode")?text(node,"serviceCode"):null);}
 public static CarrierPacking.Address origin(JsonNode node){var fields=Set.of("recipient","phone","line1","line2","city","region","postalCode","countryCode");if(node==null||!node.isObject()||!node.propertyNames().equals(fields))throw ApiValues.invalid();return new CarrierPacking.Address(text(node,"recipient"),text(node,"phone"),text(node,"line1"),node.path("line2").isNull()?null:text(node,"line2"),text(node,"city"),text(node,"region"),node.path("postalCode").isNull()?null:text(node,"postalCode"),text(node,"countryCode"));}
}
