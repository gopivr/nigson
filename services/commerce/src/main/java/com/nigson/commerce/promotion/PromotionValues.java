package com.nigson.commerce.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import tools.jackson.databind.JsonNode;

/** Closed, typed input validation for BP2 commercial promotion records. */
public final class PromotionValues {
 private static final Pattern CODE=Pattern.compile("[A-Za-z0-9_-]{1,80}");
 private static final Set<String> KINDS=Set.of("PERCENT","FIXED","FREE_DELIVERY","SPEND_REWARD","REFERRAL","DEALER");
 private PromotionValues(){}
 public record Rule(UUID id,String code,String kind,UUID marketId,Instant startsAt,Instant endsAt,JsonNode parameters,JsonNode eligibility,String stackingPolicy,boolean enabled,long revision,long version){}
 public record Coupon(UUID id,String code,boolean active,int remainingUses,long version){}
 public record Campaign(UUID id,String code,UUID ruleId,UUID marketId,int dailyLimit,String rewardBudget,JsonNode outcomes,boolean enabled,long version){}
 public record Draw(UUID drawId,Outcome outcome,String couponCode,Instant expiresAt){}
 public record Query(int page,int size,String sort){public Query{ApiValues.page(page,size);ApiValues.sort(sort,Set.of("createdAt,asc","createdAt,desc","code,asc","code,desc"));}}
 public record Outcome(String label,String kind){}
 public record Page<T>(List<T> items,int page,int size,long totalElements,int totalPages,String sort){public Page{items=List.copyOf(items);}}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.PROMOTION_RULE_VIOLATION);}
 public static JsonNode exact(JsonNode n,Set<String> keys){if(n==null||!n.isObject()||n.size()!=keys.size()||!keys.containsAll(n.propertyNames()))throw ApiValues.invalid();return n;}
 public static String code(JsonNode n,String key){if(!n.path(key).isTextual())throw ApiValues.invalid();String v=n.path(key).asString();if(!CODE.matcher(v).matches())throw ApiValues.invalid();return v;}
 public static UUID uuid(JsonNode n,String key){if(!n.path(key).isTextual())throw ApiValues.invalid();return ApiValues.uuid(n.path(key).asString());}
 public static Instant instant(JsonNode n,String key){if(!n.path(key).isTextual())throw ApiValues.invalid();return ApiValues.instant(n.path(key).asString());}
 public static boolean bool(JsonNode n,String key){if(!n.path(key).isBoolean())throw ApiValues.invalid();return n.path(key).asBoolean();}
 public static int integer(JsonNode n,String key,int min,int max){if(!n.path(key).isIntegralNumber()||!n.path(key).canConvertToInt())throw ApiValues.invalid();int v=n.path(key).intValue();if(v<min||v>max)throw ApiValues.invalid();return v;}
 public static String money(JsonNode n,String key,boolean allowZero){if(!n.path(key).isTextual())throw ApiValues.invalid();String v=n.path(key).asString();BigDecimal d=ApiValues.money(v,true);if(!allowZero&&d.signum()==0)throw ApiValues.invalid();return d.setScale(2,java.math.RoundingMode.UNNECESSARY).toPlainString();}
 public static RuleInput rule(JsonNode body,boolean create){
  Set<String> base=create?Set.of("code","kind","marketId","startsAt","endsAt","parameters","eligibility","stackingPolicy","enabled"):Set.of("kind","marketId","startsAt","endsAt","parameters","eligibility","stackingPolicy","enabled");exact(body,base);
  String code=create?code(body,"code"):null,kind=codeEnum(body,"kind",KINDS),stacking=codeEnum(body,"stackingPolicy",Set.of("EXCLUSIVE","STACKABLE"));UUID market=uuid(body,"marketId");Instant start=instant(body,"startsAt"),end=instant(body,"endsAt");if(!start.isBefore(end))throw ApiValues.invalid();
  if(bool(body,"enabled"))throw rule(); // commercial activation has a separate FINANCE approval command
  JsonNode p=body.path("parameters");validateParameters(kind,p);JsonNode e=body.path("eligibility");validateEligibility(e);
  return new RuleInput(code,kind,market,start,end,p.deepCopy(),e.deepCopy(),stacking);
 }
 public record RuleInput(String code,String kind,UUID marketId,Instant startsAt,Instant endsAt,JsonNode parameters,JsonNode eligibility,String stackingPolicy){}
 public static String codeEnum(JsonNode n,String key,Set<String> choices){if(!n.path(key).isTextual()||!choices.contains(n.path(key).asString()))throw ApiValues.invalid();return n.path(key).asString();}
 public static void validateParameters(String kind,JsonNode p){
  switch(kind){
   case "PERCENT"->{exact(p,Set.of("schemaVersion","percentage","maximumDiscount"));schema(p);BigDecimal percent=decimal(p,"percentage",false);if(percent.signum()<=0||percent.compareTo(BigDecimal.ONE)>0)throw ApiValues.invalid();nullableMoney(p,"maximumDiscount");}
   case "FIXED"->{exact(p,Set.of("schemaVersion","amount"));schema(p);money(p,"amount",false);}
   case "FREE_DELIVERY"->{exact(p,Set.of("schemaVersion","maximumDeliveryDiscount"));schema(p);nullableMoney(p,"maximumDeliveryDiscount");}
   case "SPEND_REWARD"->{exact(p,Set.of("schemaVersion","threshold","rewardAmount","maximumRewardPerOrder","holdDays","expiresDays","refundMode"));schema(p);money(p,"threshold",false);money(p,"rewardAmount",false);money(p,"maximumRewardPerOrder",false);integer(p,"holdDays",0,365);integer(p,"expiresDays",1,3650);codeEnum(p,"refundMode",Set.of("PROPORTIONAL","FULL_ON_ANY_REFUND"));}
   case "REFERRAL"->{exact(p,Set.of("schemaVersion","minimumSettledSpend","referrerReward","referredReward","holdDays","expiresDays","maximumRewardsPerReferrer","refundMode"));schema(p);money(p,"minimumSettledSpend",true);money(p,"referrerReward",false);money(p,"referredReward",true);integer(p,"holdDays",0,365);integer(p,"expiresDays",1,3650);integer(p,"maximumRewardsPerReferrer",1,1000000);codeEnum(p,"refundMode",Set.of("PROPORTIONAL","FULL_ON_ANY_REFUND"));}
   case "DEALER"->{exact(p,Set.of("schemaVersion","minimumSettledSpend","maximumIssue","expiresDays","requiresSecondActor"));schema(p);money(p,"minimumSettledSpend",true);money(p,"maximumIssue",false);integer(p,"expiresDays",1,3650);bool(p,"requiresSecondActor");}
   default->throw ApiValues.invalid();
  }
 }
 private static void schema(JsonNode n){if(!n.path("schemaVersion").isIntegralNumber()||n.path("schemaVersion").asInt()!=1)throw ApiValues.invalid();}
 private static BigDecimal decimal(JsonNode n,String key,boolean zero){if(!n.path(key).isTextual())throw ApiValues.invalid();BigDecimal d=ApiValues.money(n.path(key).asString(),true);if(!zero&&d.signum()<=0)throw ApiValues.invalid();return d;}
 private static void nullableMoney(JsonNode n,String key){if(n.path(key).isNull())return;money(n,key,false);}
 public static CouponInput coupon(JsonNode n){if(n==null||!n.isObject()||!Set.of("ruleId","code","maximumUses","maximumUsesPerParty","expiresAt","partyId").containsAll(n.propertyNames())||!n.propertyNames().containsAll(Set.of("ruleId","code","maximumUses","maximumUsesPerParty","expiresAt")))throw ApiValues.invalid();UUID rule=uuid(n,"ruleId");String code=code(n,"code");UUID party=!n.has("partyId")||n.path("partyId").isNull()?null:uuid(n,"partyId");int uses=integer(n,"maximumUses",1,1000000),perParty=integer(n,"maximumUsesPerParty",1,1000000);if(perParty>uses)throw ApiValues.invalid();Instant expires=instant(n,"expiresAt");return new CouponInput(rule,code,party,uses,perParty,expires);}
 public record CouponInput(UUID ruleId,String code,UUID partyId,int maximumUses,int maximumUsesPerParty,Instant expiresAt){}
 public static CouponToggle couponToggle(JsonNode n){exact(n,Set.of("active","reason"));return new CouponToggle(bool(n,"active"),reason(n));}
 public static CampaignToggle campaignToggle(JsonNode n){exact(n,Set.of("enabled","reason"));return new CampaignToggle(bool(n,"enabled"),reason(n));}
 public record CouponToggle(boolean active,String reason){}public record CampaignToggle(boolean enabled,String reason){}
 public static String reason(JsonNode n){if(!n.path("reason").isTextual())throw ApiValues.invalid();String v=n.path("reason").asString();if(v.strip().length()<10||v.length()>300||v.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return v;}
 public static CampaignInput campaign(JsonNode n){exact(n,Set.of("code","ruleId","marketId","dailyLimit","rewardBudget","outcomes","enabled"));String code=code(n,"code");UUID rule=uuid(n,"ruleId"),market=uuid(n,"marketId");int daily=integer(n,"dailyLimit",1,1000000);String budget=money(n,"rewardBudget",true);JsonNode outcomes=n.path("outcomes");if(!outcomes.isArray()||outcomes.isEmpty()||outcomes.size()>100)throw ApiValues.invalid();List<OutcomeInput> values=new ArrayList<>();long total=0;for(JsonNode o:outcomes){exact(o,Set.of("label","kind","weight","benefit"));if(!o.path("label").isTextual())throw ApiValues.invalid();String label=o.path("label").asString();if(label.isBlank()||label.length()>100||label.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();String kind=codeEnum(o,"kind",Set.of("COUPON","FREE_DELIVERY","RETRY","NONE"));int weight=integer(o,"weight",1,1000000);total+=weight;if(total>100000000L)throw ApiValues.invalid();JsonNode benefit=o.path("benefit").deepCopy();validateBenefit(kind,benefit);values.add(new OutcomeInput(label,kind,weight,benefit));}if(bool(n,"enabled"))throw rule();return new CampaignInput(code,rule,market,daily,budget,values);}
 private static void validateBenefit(String kind,JsonNode b){switch(kind){case "COUPON","FREE_DELIVERY"->{exact(b,Set.of("schemaVersion","ruleId","expiresHours","maximumDiscount"));schema(b);uuid(b,"ruleId");integer(b,"expiresHours",1,8760);money(b,"maximumDiscount",false);}case "RETRY"->{exact(b,Set.of("schemaVersion","extraDraws"));schema(b);integer(b,"extraDraws",1,1);}case "NONE"->{exact(b,Set.of("schemaVersion"));schema(b);}default->throw ApiValues.invalid();}}
 public record OutcomeInput(String label,String kind,int weight,JsonNode benefit){}public record CampaignInput(String code,UUID ruleId,UUID marketId,int dailyLimit,String rewardBudget,List<OutcomeInput> outcomes){public CampaignInput{outcomes=List.copyOf(outcomes);}}
 public static void validateEligibility(JsonNode n){exact(n,Set.of("schemaVersion","segment","productIds","categoryIds","minimumSpend","minimumQuantity","firstSettledPurchaseOnly"));schema(n);codeEnum(n,"segment",Set.of("RETAIL","DISTRIBUTOR","ANY"));if(!n.path("productIds").isArray()||n.path("productIds").size()>100||!n.path("categoryIds").isArray()||n.path("categoryIds").size()>100||!n.path("firstSettledPurchaseOnly").isBoolean())throw ApiValues.invalid();Set<String> ids=new HashSet<>();for(JsonNode a:List.of(n.path("productIds"),n.path("categoryIds")))for(JsonNode x:a){if(!x.isTextual()||!ids.add(x.asString()))throw ApiValues.invalid();ApiValues.uuid(x.asString());}if(!n.path("minimumSpend").isNull())money(n,"minimumSpend",true);if(!n.path("minimumQuantity").isNull()){if(!n.path("minimumQuantity").isTextual())throw ApiValues.invalid();BigDecimal q=ApiValues.quantity(n.path("minimumQuantity").asString());if(q.signum()<0)throw ApiValues.invalid();}}
}
