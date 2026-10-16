package com.nigson.commerce.fulfillment;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.commerce.pricing.PricingValues;
import com.nigson.commerce.inventory.StockLedger;
/** Closed staff commands. All prices are explicit NGN minor-unit strings. */
public final class FulfillmentValues {
 private FulfillmentValues(){}
 public record RuleInput(UUID marketId,String countryCode,String regionCode,String postalPrefix,String fee,String freeAbove,int priority,boolean active){}
 public record Rule(UUID id,UUID marketId,String countryCode,String regionCode,String postalPrefix,String fee,String freeAbove,int priority,boolean active,long version){}
 public record LineInput(UUID orderLineId,UUID balanceId,String quantity){}
 public record ShipmentInput(List<LineInput> lines,String provider,String providerReference,String trackingUrl){}
 public record ShipmentLine(UUID orderLineId,String quantity){}
 public record Shipment(UUID id,UUID orderId,String status,List<ShipmentLine> lines,String trackingUrl,long version){}
 public record ReturnInput(List<LineInput> lines,StockLedger.ReturnKind kind,String receiptReference,String reason){}
 public record Return(UUID returnId,UUID orderId,List<ShipmentLine> lines){}
 public record Delivery(Instant deliveredAt,String reason){}
 public record Dispatch(Instant dispatchedAt,String reason){}
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.FULFILLMENT_RULE_VIOLATION);}
 private static void closed(JsonNode input,Set<String> required,Set<String> allowed){if(input==null||!input.isObject()||!allowed.containsAll(input.propertyNames())||!input.propertyNames().containsAll(required))throw ApiValues.invalid();}
 private static String text(JsonNode input,String key,int max,boolean required){var value=input.get(key);if(value==null){if(required)throw ApiValues.invalid();return null;}if(!value.isString())throw ApiValues.invalid();String raw=value.asString();if(raw.isEmpty()||raw.length()>max||!raw.equals(raw.strip())||raw.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return raw;}
 private static String money(JsonNode input,String key,boolean required){String raw=text(input,key,20,required);if(raw==null)return null;try{return PricingValues.minor(raw,2).toPlainString();}catch(Problem.Failure failure){if(failure.getMessage().equals("PRICING_RULE_VIOLATION"))throw rule();throw failure;}}
 public static RuleInput deliveryRule(JsonNode input){closed(input,Set.of("marketId","countryCode","fee","priority","active"),Set.of("marketId","countryCode","regionCode","postalPrefix","fee","freeAbove","priority","active"));String country=text(input,"countryCode",2,true);if(!country.equals("NG"))throw rule();String region=text(input,"regionCode",80,false),postal=text(input,"postalPrefix",24,false);if(region!=null&&!region.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"))throw ApiValues.invalid();var priority=input.get("priority");var active=input.get("active");if(!priority.isIntegralNumber()||!priority.canConvertToInt()||!active.isBoolean())throw ApiValues.invalid();return new RuleInput(ApiValues.uuid(text(input,"marketId",36,true)),country,region,postal,money(input,"fee",true),money(input,"freeAbove",false),priority.asInt(),active.asBoolean());}
 private static List<LineInput> lines(JsonNode input){var rows=input.get("lines");if(rows==null||!rows.isArray()||rows.isEmpty()||rows.size()>100)throw ApiValues.invalid();var lines=new ArrayList<LineInput>();var seen=new HashSet<String>();for(var row:rows){closed(row,Set.of("orderLineId","balanceId","quantity"),Set.of("orderLineId","balanceId","quantity"));UUID order=ApiValues.uuid(text(row,"orderLineId",36,true)),balance=ApiValues.uuid(text(row,"balanceId",36,true));String q=text(row,"quantity",21,true);BigDecimal quantity=ApiValues.quantity(q);if(quantity.signum()<=0)throw ApiValues.invalid();if(!seen.add(order+":"+balance))throw ApiValues.invalid();lines.add(new LineInput(order,balance,PricingValues.decimal(quantity)));}return List.copyOf(lines);}
 public static ShipmentInput shipment(JsonNode input){closed(input,Set.of("lines"),Set.of("lines","provider","providerReference","trackingUrl"));String provider=text(input,"provider",40,false);if(provider==null)provider="MANUAL";if(!Set.of("MANUAL","FEDEX").contains(provider))throw rule();String reference=text(input,"providerReference",120,false),tracking=text(input,"trackingUrl",1000,false);if(tracking!=null)https(tracking);if(provider.equals("FEDEX")&&(reference!=null||tracking!=null))throw rule();return new ShipmentInput(lines(input),provider,reference,tracking);}
 public static void https(String value){try{var uri=java.net.URI.create(value);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443||uri.getRawFragment()!=null)throw ApiValues.invalid();}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}}
 public static String reason(JsonNode input){closed(input,Set.of("reason"),Set.of("reason"));return reasonValue(input);}
 private static String reasonValue(JsonNode input){String reason=text(input,"reason",500,true);if(reason.length()<10)throw ApiValues.invalid();return reason;}
 public static Dispatch dispatch(JsonNode input){closed(input,Set.of("reason"),Set.of("reason","dispatchedAt"));String at=text(input,"dispatchedAt",40,false);try{return new Dispatch(at==null?null:Instant.parse(at),reasonValue(input));}catch(java.time.format.DateTimeParseException invalid){throw ApiValues.invalid();}}
 public static Delivery delivered(JsonNode input){closed(input,Set.of("deliveredAt","reason"),Set.of("deliveredAt","reason"));try{return new Delivery(Instant.parse(text(input,"deliveredAt",40,true)),reasonValue(input));}catch(java.time.format.DateTimeParseException invalid){throw ApiValues.invalid();}}
 public static ReturnInput returned(JsonNode input){closed(input,Set.of("lines","kind","reason"),Set.of("lines","kind","receiptReference","reason"));StockLedger.ReturnKind kind;try{kind=StockLedger.ReturnKind.valueOf(text(input,"kind",30,true));}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}String receipt=text(input,"receiptReference",120,kind==StockLedger.ReturnKind.PHYSICAL_RETURN);if(receipt!=null&&!receipt.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,119}"))throw ApiValues.invalid();var rows=lines(input);if(rows.stream().map(LineInput::orderLineId).distinct().count()!=rows.size())throw ApiValues.invalid();return new ReturnInput(rows,kind,receipt,reasonValue(input));}
}
