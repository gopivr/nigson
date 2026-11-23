package com.nigson.commerce.order;
import java.util.*;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.commerce.payment.PaymentValues;
/** Customer-facing values never expose stock locations, provider accounts or staff identities. */
public final class ReturnValues {
 private ReturnValues(){}
 public record Request(UUID orderLineId,String quantity,String reason){public Request{ApiValues.uuid(orderLineId==null?null:orderLineId.toString());quantity=ApiValues.quantity(quantity).stripTrailingZeros().toPlainString();reason=text(reason,10,500);}}
 public record Review(boolean approve,String reason,String instructions){public Review{reason=text(reason,10,500);if(approve)instructions=text(instructions,10,1000);else if(instructions!=null)throw ApiValues.invalid();}}
 public record Receipt(String receiptReference,boolean restock,String reason){public Receipt{receiptReference=text(receiptReference,1,120);if(!receiptReference.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}"))throw ApiValues.invalid();reason=text(reason,10,500);}}
 public record Refund(String amount,String reason){public Refund{amount=PaymentValues.money(amount,true);reason=text(reason,10,500);}}
 public record Line(UUID orderLineId,String name,String orderedQuantity,String availableQuantity,int quantityScale){}
 public record Item(UUID id,UUID orderId,UUID orderLineId,String productName,String quantity,String reason,String policyRevision,String state,String decisionReason,String instructions,String receiptReference,Boolean restock,Instant receivedAt,UUID refundId,String refundState,long version,Instant createdAt){}
 public record View(UUID orderId,long orderVersion,boolean delivered,List<Line> lines,List<Item> requests,String refundableAmount,boolean paystackRefundAvailable){}
 private static String text(String v,int min,int max){if(v==null||!v.equals(v.strip())||v.length()<min||v.length()>max||v.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return v;}
 private static String string(JsonNode n,String key){var v=n.get(key);if(v==null||!v.isString())throw ApiValues.invalid();return v.asString();}
 private static boolean bool(JsonNode n,String key){var v=n.get(key);if(v==null||!v.isBoolean())throw ApiValues.invalid();return v.asBoolean();}
 private static void closed(JsonNode n,Set<String> required,Set<String> optional){if(n==null||!n.isObject()||!n.propertyNames().containsAll(required))throw ApiValues.invalid();var allowed=new HashSet<>(required);allowed.addAll(optional);if(!allowed.containsAll(n.propertyNames()))throw ApiValues.invalid();}
 public static Request request(JsonNode n){closed(n,Set.of("orderLineId","quantity","reason"),Set.of());return new Request(ApiValues.uuid(string(n,"orderLineId")),string(n,"quantity"),string(n,"reason"));}
 public static Review review(JsonNode n){closed(n,Set.of("approve","reason"),Set.of("instructions"));return new Review(bool(n,"approve"),string(n,"reason"),n.has("instructions")?string(n,"instructions"):null);}
 public static Receipt receipt(JsonNode n){closed(n,Set.of("receiptReference","restock","reason"),Set.of());return new Receipt(string(n,"receiptReference"),bool(n,"restock"),string(n,"reason"));}
 public static Refund refund(JsonNode n){closed(n,Set.of("amount","reason"),Set.of());return new Refund(string(n,"amount"),string(n,"reason"));}
}
