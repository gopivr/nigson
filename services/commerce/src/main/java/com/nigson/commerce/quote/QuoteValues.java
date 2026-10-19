package com.nigson.commerce.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.ApiValues;

/** Closed wire values for immutable negotiated quote revisions. */
public final class QuoteValues {
 private QuoteValues(){}
 public record LineInput(UUID skuId,String quantity,String agreedUnitAmount,String discountAmount,String taxAmount){}
 public record CreateInput(UUID partyId,UUID organizationId,UUID marketId,UUID sourceLeadId,Instant validUntil,String terms,List<LineInput> lines){}
 public record RevisionInput(Instant validUntil,String terms,List<LineInput> lines){}
 public record Decision(boolean approve,String reason){}
 public record Offer(UUID revisionId,String reason){}
 public record AcceptInput(UUID revisionId,UUID addressId,UUID carrierQuoteId){ }
 public record LeadContext(UUID id,UUID partyId,UUID organizationId,String state){}
 public record Line(UUID id,UUID skuId,String quantity,String unitAmount,String discountAmount,String taxAmount,String lineTotal){}
 public record Quote(UUID id,String state,UUID revisionId,int revision,String total,String currency,Instant validUntil,List<Line> lines,long version){}
 private static String str(JsonNode n,String k,int max){JsonNode v=n.get(k);if(v==null||!v.isString()||v.asString().isBlank()||v.asString().length()>max)throw ApiValues.invalid();return v.asString();}
 private static Instant instant(JsonNode n,String k){try{return Instant.parse(str(n,k,40));}catch(RuntimeException e){throw ApiValues.invalid();}}
 private static List<LineInput> lines(JsonNode n){JsonNode a=n.get("lines");if(a==null||!a.isArray()||a.isEmpty()||a.size()>100)throw ApiValues.invalid();var out=new ArrayList<LineInput>();var seen=new HashSet<UUID>();for(JsonNode l:a){if(!l.isObject()||!l.propertyNames().equals(Set.of("skuId","quantity","agreedUnitAmount","discountAmount","taxAmount")))throw ApiValues.invalid();UUID sku=ApiValues.uuid(str(l,"skuId",36));String q=decimal(l,"quantity",20,3),unit=decimal(l,"agreedUnitAmount",19,4),discount=decimal(l,"discountAmount",19,4),tax=decimal(l,"taxAmount",19,4);if(!seen.add(sku)||new BigDecimal(q).signum()<=0||new BigDecimal(unit).signum()<0||new BigDecimal(discount).signum()<0||new BigDecimal(tax).signum()<0)throw ApiValues.invalid();out.add(new LineInput(sku,q,unit,discount,tax));}return List.copyOf(out);}
 private static String decimal(JsonNode n,String k,int precision,int scale){String s=str(n,k,32);try{BigDecimal d=new BigDecimal(s);if(d.signum()<0||d.scale()>scale||d.precision()>precision||!d.toPlainString().equals(s))throw ApiValues.invalid();return s;}catch(NumberFormatException e){throw ApiValues.invalid();}}
 private static void closed(JsonNode n,Set<String> required,Set<String> optional){var allowed=new HashSet<>(required);allowed.addAll(optional);if(n==null||!n.isObject()||!n.propertyNames().containsAll(required)||!allowed.containsAll(n.propertyNames()))throw ApiValues.invalid();}
 public static CreateInput create(JsonNode n){closed(n,Set.of("partyId","marketId","sourceLeadId","validUntil","terms","lines"),Set.of("organizationId"));return new CreateInput(ApiValues.uuid(str(n,"partyId",36)),n.has("organizationId")?ApiValues.uuid(str(n,"organizationId",36)):null,ApiValues.uuid(str(n,"marketId",36)),ApiValues.uuid(str(n,"sourceLeadId",36)),instant(n,"validUntil"),str(n,"terms",4000),lines(n));}
 public static RevisionInput revision(JsonNode n){closed(n,Set.of("validUntil","terms","lines"),Set.of());return new RevisionInput(instant(n,"validUntil"),str(n,"terms",4000),lines(n));}
 public static Decision decision(JsonNode n){closed(n,Set.of("approve","reason"),Set.of());if(!n.path("approve").isBoolean())throw ApiValues.invalid();return new Decision(n.path("approve").asBoolean(),str(n,"reason",300));}
 public static Offer offer(JsonNode n){closed(n,Set.of("revisionId","reason"),Set.of());return new Offer(ApiValues.uuid(str(n,"revisionId",36)),str(n,"reason",300));}
 public static AcceptInput accept(JsonNode n){closed(n,Set.of("revisionId","addressId","carrierQuoteId"),Set.of());return new AcceptInput(ApiValues.uuid(str(n,"revisionId",36)),ApiValues.uuid(str(n,"addressId",36)),ApiValues.uuid(str(n,"carrierQuoteId",36)));}
}
