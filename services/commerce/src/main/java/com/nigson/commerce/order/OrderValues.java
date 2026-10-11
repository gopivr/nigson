package com.nigson.commerce.order;
import java.util.*;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
/** Closed wire values. Commerce snapshots never expose provider/account/internal metadata. */
public final class OrderValues {
 private OrderValues(){}
 public static final Set<String> STATES=Set.of("PENDING","PAID","PROCESSING","SHIPPED","DELIVERED","CANCELLED","PAYMENT_HOLD","REFUNDING","REFUNDED");
 public static final Set<String> PAYMENT_STATES=Set.of("UNPAID","PENDING","PAID","PARTIALLY_REFUNDED","REFUNDED","FAILED");
 public record CheckoutInput(UUID checkoutQuoteId,String quoteHash,String paymentMethod){}
 public record Totals(String subtotal,String discount,String tax,String delivery,String grandTotal,String refunded){}
 public record Line(UUID id,UUID skuId,String skuCode,String name,String quantity,String unitAmount,String lineTotal){}
 public record Address(String label,String recipient,String phone,String line1,String line2,String city,String region,String postalCode,String countryCode,boolean isDefault){}
 public record Order(UUID id,String orderNumber,String status,String paymentStatus,String fulfillmentStatus,String currency,Totals totals,List<Line> lines,Address address,long version,Instant createdAt){}
 public record Payment(UUID attemptId,String state,String checkoutUrl,String reference){}
 public record Checkout(Order order,Payment payment){}
 public record ProofInput(UUID partyId,UUID orderLineId,UUID productId){}
 public record Proof(boolean eligible,String reasonCode,long revision){}
 public record Page(List<Order> items,int page,int size,long totalElements,long totalPages){}
 public record Query(UUID organizationId,String status,String paymentStatus,Instant from,Instant to,int page,int size,String sort){
  public Query{ApiValues.page(page,size);if(status!=null&&!STATES.contains(status)||paymentStatus!=null&&!PAYMENT_STATES.contains(paymentStatus))throw ApiValues.invalid();ApiValues.sort(sort,Set.of("createdAt,asc","createdAt,desc","orderNumber,asc","orderNumber,desc"));if(from!=null&&to!=null&&!from.isBefore(to))throw ApiValues.invalid();}
 }
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.ORDER_RULE_VIOLATION);}
 private static void closed(JsonNode n,Set<String> fields){if(n==null||!n.isObject()||!n.propertyNames().equals(fields))throw ApiValues.invalid();}
 private static String text(JsonNode n,String key,int min,int max){var value=n.get(key);if(value==null||!value.isString())throw ApiValues.invalid();String raw=value.asString();if(raw.length()<min||raw.length()>max||!raw.equals(raw.strip())||raw.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return raw;}
 public static CheckoutInput checkout(JsonNode n){closed(n,Set.of("checkoutQuoteId","quoteHash","paymentMethod"));String hash=text(n,"quoteHash",64,64),method=text(n,"paymentMethod",6,6);if(!hash.matches("[a-f0-9]{64}")||!method.equals("HOSTED"))throw ApiValues.invalid();return new CheckoutInput(ApiValues.uuid(text(n,"checkoutQuoteId",36,36)),hash,method);}
 public static String reason(JsonNode n){closed(n,Set.of("reason"));return text(n,"reason",10,500);}
 public static ProofInput proof(JsonNode n){closed(n,Set.of("partyId","orderLineId","productId"));return new ProofInput(ApiValues.uuid(text(n,"partyId",36,36)),ApiValues.uuid(text(n,"orderLineId",36,36)),ApiValues.uuid(text(n,"productId",36,36)));}
}
