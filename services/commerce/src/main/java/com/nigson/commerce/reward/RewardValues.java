package com.nigson.commerce.reward;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import tools.jackson.databind.JsonNode;

public final class RewardValues {
 private static final Pattern CODE=Pattern.compile("[A-Z0-9]{16,60}");
 public record Entry(UUID id,String kind,String amount,String currency,java.time.Instant createdAt){}
 public record Balance(String currency,String amount){}
 public record Ledger(List<Balance> availableByCurrency,List<Entry> entries){public Ledger{availableByCurrency=List.copyOf(availableByCurrency);entries=List.copyOf(entries);}}
 public record Referral(String code,String link,int qualifiedReferralCount){}
 public record Attribution(UUID attributionId,String state){}
 public record DealerReward(UUID entryId,UUID couponId){}
 private RewardValues(){}
 public static void emptyObject(JsonNode n){if(n==null||!n.isObject()||!n.isEmpty())throw ApiValues.invalid();}
 public static String referralCode(JsonNode n){if(n==null||!n.isObject()||n.size()!=1||!n.has("code")||!n.get("code").isString())throw ApiValues.invalid();String value=n.get("code").asString();if(!CODE.matcher(value).matches())throw ApiValues.invalid();return value;}
 public static BigDecimal amount(String value,int minor){try{BigDecimal n=new BigDecimal(value);if(!n.toPlainString().equals(value)||n.signum()<=0||n.scale()>minor||n.precision()>19)throw ApiValues.invalid();return n;}catch(NumberFormatException invalid){throw ApiValues.invalid();}}
 public static BigDecimal nonnegative(String value,int minor){try{BigDecimal n=new BigDecimal(value);if(!n.toPlainString().equals(value)||n.signum()<0||n.scale()>minor||n.precision()>19)throw ApiValues.invalid();return n;}catch(NumberFormatException invalid){throw ApiValues.invalid();}}
 public static JsonNode exact(JsonNode value,Set<String> keys){if(value==null||!value.isObject()||value.size()!=keys.size()||!keys.containsAll(value.propertyNames()))throw ApiValues.invalid();return value;}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.REWARD_RULE_VIOLATION);}
}
