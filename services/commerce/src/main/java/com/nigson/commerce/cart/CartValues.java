package com.nigson.commerce.cart;
import java.util.*;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
public final class CartValues {
 private CartValues(){}
 public record Create(UUID marketId,UUID organizationId){}
 public record Quantity(String quantity){}
 public record Line(UUID id,UUID skuId,String quantity){}
 public record Cart(UUID id,UUID marketId,UUID organizationId,String state,List<Line> lines,long version,Instant lastActivityAt){}
 public record Removed(UUID id,long version){}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.CART_RULE_VIOLATION);}
 private static String raw(JsonNode n,String field){var v=n.get(field);if(v==null||!v.isString())throw ApiValues.invalid();return v.asString();}
 public static Create create(JsonNode input){if(input==null||!input.isObject()||!input.has("marketId")||!Set.of("marketId","organizationId").containsAll(input.propertyNames()))throw ApiValues.invalid();return new Create(ApiValues.uuid(raw(input,"marketId")),input.has("organizationId")?ApiValues.uuid(raw(input,"organizationId")):null);}
 public static Quantity quantity(JsonNode input){if(input==null||!input.isObject()||input.size()!=1||!input.has("quantity"))throw ApiValues.invalid();return new Quantity(com.nigson.commerce.pricing.PricingValues.decimal(ApiValues.quantity(raw(input,"quantity"))));}
 public static void version(long expected){if(expected<0||expected==Long.MAX_VALUE)throw ApiValues.invalid();}
}
