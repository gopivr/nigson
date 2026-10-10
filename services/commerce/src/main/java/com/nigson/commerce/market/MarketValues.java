package com.nigson.commerce.market;
import java.util.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
/** Draft policies carry no approval. Approved policy parsing never supplies a rate or destination. */
public final class MarketValues {
 private MarketValues(){}
 public record Create(String code,String name,String currency){}
 public record Eligibility(boolean eligible,String reason){}
 public record Book(UUID priceBookId,String reason){}
 public record Restriction(UUID productId,UUID marketId,boolean eligible,long version){}
 public record PublicMarket(UUID id,String code,String currency,List<String> destinations,long policyRevision){}
 public record Destination(String countryCode,JsonNode addressRules,boolean deliveryEnabled){}
 public record Market(UUID id,String code,String name,String currency,boolean enabled,List<Destination> destinations,JsonNode taxPolicy,String returnsPolicyRevision,UUID retailPriceBookId,long policyRevision,long version){}
 public record TaxPolicy(String mode,BigDecimal rate,String approvalReference){}
 public static void closed(JsonNode value,Set<String> fields){if(value==null||!value.isObject()||value.size()!=fields.size()||!fields.containsAll(value.propertyNames())||EventCodec.JSON.writeValueAsBytes(value).length>16384)throw ApiValues.invalid();}
 public static String text(JsonNode value,String field,int min,int max){var n=value.get(field);if(n==null||!n.isString())throw ApiValues.invalid();String raw=n.asString();if(raw.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();String s=Normalizer.normalize(raw,Normalizer.Form.NFC).strip();if(s.length()<min||s.length()>max)throw ApiValues.invalid();return s;}
 public static Create create(JsonNode value){closed(value,Set.of("code","name","currency"));String code=text(value,"code",2,32),currency=text(value,"currency",3,3);if(!code.matches("[A-Z][A-Z0-9_-]{1,31}")||!currency.matches("[A-Z]{3}"))throw ApiValues.invalid();try{Currency.getInstance(currency);}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}return new Create(code,text(value,"name",1,100),currency);}
 public static Eligibility eligibility(JsonNode value){closed(value,Set.of("eligible","reason"));if(!value.get("eligible").isBoolean())throw ApiValues.invalid();return new Eligibility(value.get("eligible").asBoolean(),text(value,"reason",10,300));}
 public static Book book(JsonNode value){closed(value,Set.of("priceBookId","reason"));return new Book(ApiValues.uuid(text(value,"priceBookId",36,36)),text(value,"reason",10,300));}
 public static TaxPolicy tax(JsonNode value){closed(value,Set.of("schemaVersion","mode","rate","rounding","approvalReference"));if(!value.get("schemaVersion").isIntegralNumber()||!value.get("schemaVersion").canConvertToInt()||value.get("schemaVersion").asInt()!=1||!text(value,"rounding",7,7).equals("HALF_UP"))throw ApiValues.invalid();String mode=text(value,"mode",4,9),rate=text(value,"rate",1,20);if(!Set.of("NONE","EXCLUSIVE","INCLUSIVE").contains(mode)||!rate.matches("(0(\\.[0-9]{1,18})?|1(\\.0{1,18})?)"))throw ApiValues.invalid();BigDecimal amount=new BigDecimal(rate);if(mode.equals("NONE")&&amount.signum()!=0)throw ApiValues.invalid();return new TaxPolicy(mode,amount,text(value,"approvalReference",1,120));}
 /** Reuse the owned address contract and pinned bounded RE2/J engine. */
 public static void address(JsonNode value){com.nigson.commerce.party.AddressRules.parse(value);}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.MARKET_RULE_VIOLATION);}
}
