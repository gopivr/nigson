package com.nigson.commerce.payment;

import java.util.*;
import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;

/** Closed financial values: exact NGN minor units and bounded distinct refund lines. */
public final class PaymentValues {
 private PaymentValues(){}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.PAYMENT_RULE_VIOLATION);}
 public static final Set<String> REFUND_STATES=Set.of("REQUESTED","APPROVED","SUBMITTING","PENDING","UNKNOWN","SUCCEEDED","FAILED","REJECTED");
 public record Payment(UUID orderId,String state,boolean settled,String reference,String checkoutUrl){}
 public record Refund(UUID id,String state,String amount,String currency,long version){}
 public record RefundDetail(UUID id,String state,String amount,String currency,long version,String providerReference){}
 public record RefundLine(UUID orderLineId,String quantity,String amount){public RefundLine{uuid(orderLineId);quantity=ApiValues.quantity(quantity).stripTrailingZeros().toPlainString();amount=money(amount,false);}}
 public record RefundInput(String amount,String reason,List<RefundLine> lines){public RefundInput{amount=money(amount,true);reason=PaymentValues.reason(reason,10);if(lines==null||lines.size()>100)throw ApiValues.invalid();lines=List.copyOf(lines);var ids=new HashSet<UUID>();for(var line:lines)if(!ids.add(line.orderLineId()))throw ApiValues.invalid();}}
 public record Approval(boolean approve,String reason){public Approval{reason=PaymentValues.reason(reason,1);}}
 public record Reconciliation(UUID jobId,int limit,String cursor){public Reconciliation{uuid(jobId);if(limit<1||limit>100||cursor!=null&&!cursor.matches("[A-Za-z0-9_-]{1,256}"))throw ApiValues.invalid();}}
 public record ReconciliationResult(int processed,String nextCursor,int mismatches){}
 private static void uuid(UUID id){if(id==null)throw ApiValues.invalid();ApiValues.uuid(id.toString());}
 public static String money(String raw,boolean positive){BigDecimal value=ApiValues.money(raw,true);if(value.scale()>2||positive&&value.signum()==0)throw ApiValues.invalid();return value.setScale(2).toPlainString();}
 private static String reason(String raw,int min){if(raw==null||raw.length()<min||raw.length()>500||!raw.equals(raw.strip())||raw.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return raw;}
 private static void closed(JsonNode node,Set<String> required,Set<String> optional){if(node==null||!node.isObject()||!node.propertyNames().containsAll(required))throw ApiValues.invalid();var allowed=new HashSet<>(required);allowed.addAll(optional);if(!allowed.containsAll(node.propertyNames()))throw ApiValues.invalid();}
 private static String text(JsonNode node,String field){var value=node.get(field);if(value==null||!value.isString())throw ApiValues.invalid();return value.asString();}
 public static RefundInput refund(JsonNode node){closed(node,Set.of("amount","reason"),Set.of("lines"));var lines=new ArrayList<RefundLine>();if(node.has("lines")){var items=node.get("lines");if(!items.isArray()||items.isEmpty()||items.size()>100)throw ApiValues.invalid();for(var line:items){closed(line,Set.of("orderLineId","quantity","amount"),Set.of());lines.add(new RefundLine(ApiValues.uuid(text(line,"orderLineId")),text(line,"quantity"),text(line,"amount")));}}return new RefundInput(text(node,"amount"),text(node,"reason"),lines);}
 public static Approval approval(JsonNode node){closed(node,Set.of("approve","reason"),Set.of());if(!node.get("approve").isBoolean())throw ApiValues.invalid();return new Approval(node.get("approve").asBoolean(),text(node,"reason"));}
 public static Reconciliation reconciliation(JsonNode node){closed(node,Set.of("jobId","limit"),Set.of("cursor"));var limit=node.get("limit");if(!limit.isIntegralNumber()||!limit.canConvertToInt())throw ApiValues.invalid();return new Reconciliation(ApiValues.uuid(text(node,"jobId")),limit.asInt(),node.has("cursor")?text(node,"cursor"):null);}
}
