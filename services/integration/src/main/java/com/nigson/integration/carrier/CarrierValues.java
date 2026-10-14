package com.nigson.integration.carrier;

import java.math.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;

/** Closed private logistics values; no browser account, transport URL or provider request is accepted. */
public final class CarrierValues {
 private CarrierValues(){}
 public record Address(String recipient,String phone,String line1,String line2,String city,String region,String postalCode,String countryCode){
  public Address{text(recipient,160);text(line1,200);optional(line2,200);text(city,100);text(region,100);optional(postalCode,24);if(phone==null||!phone.matches("\\+[1-9][0-9]{7,14}")||!"NG".equals(countryCode))throw ApiValues.invalid();}
  @Override public String toString(){return "CarrierAddress[protected]";}
 }
 public record Parcel(UUID skuId,String quantity,String weightKg,String lengthCm,String widthCm,String heightCm){
  public Parcel{id(skuId);ApiValues.quantity(quantity);ApiValues.quantity(weightKg);dimension(lengthCm);dimension(widthCm);dimension(heightCm);}
 }
 public record QuoteInput(UUID commandId,UUID cartId,String market,String currency,Address shipper,Address destination,List<Parcel> packages,String serviceCode){
  public QuoteInput{id(commandId);id(cartId);if(!"NG".equals(market)||!"NGN".equals(currency)||shipper==null||destination==null||packages==null||packages.isEmpty()||packages.size()>100)throw ApiValues.invalid();packages=List.copyOf(packages);if(serviceCode!=null)service(serviceCode);}
  @Override public String toString(){return "CarrierQuoteInput[protected]";}
 }
 public record Quote(String provider,String serviceCode,String currency,String amount,Instant expiresAt,String providerReference){
  public Quote{if(!"FEDEX".equals(provider)||!"NGN".equals(currency)||expiresAt==null)throw rule();service(serviceCode);money(amount);optional(providerReference,160);}
 }
 public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);}
 private static void id(UUID id){if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();}
 private static void optional(String value,int max){if(value!=null)text(value,max);}
 private static String text(String value,int max){if(value==null||value.isBlank()||value.length()>max||!value.equals(value.strip())||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return value;}
 public static String service(String value){if(value==null||!value.matches("[A-Z][A-Z0-9_]{0,79}"))throw ApiValues.invalid();return value;}
 private static BigDecimal dimension(String value){var decimal=ApiValues.money(value,true);if(decimal.signum()<=0)throw ApiValues.invalid();try{decimal.setScale(0,RoundingMode.CEILING).intValueExact();}catch(ArithmeticException invalid){throw ApiValues.invalid();}return decimal;}
 private static BigDecimal money(String value){if(value==null||!value.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))throw rule();return new BigDecimal(value).setScale(2);}
 private static void closed(JsonNode node,Set<String> required,Set<String> allowed){if(node==null||!node.isObject()||!node.propertyNames().containsAll(required)||!allowed.containsAll(node.propertyNames()))throw ApiValues.invalid();}
 private static String str(JsonNode node,String name){var value=node.get(name);if(value==null||!value.isString())throw ApiValues.invalid();return value.asString();}
 private static String nullable(JsonNode node,String name){var value=node.get(name);return value==null||value.isNull()?null:str(node,name);}
 public static Address address(JsonNode node){var fields=Set.of("recipient","phone","line1","line2","city","region","postalCode","countryCode");closed(node,fields,fields);return new Address(str(node,"recipient"),str(node,"phone"),str(node,"line1"),nullable(node,"line2"),str(node,"city"),str(node,"region"),nullable(node,"postalCode"),str(node,"countryCode"));}
 public static QuoteInput quoteInput(JsonNode node){
  closed(node,Set.of("commandId","cartId","market","currency","shipper","destination","packages"),Set.of("commandId","cartId","market","currency","shipper","destination","packages","serviceCode"));var rows=node.get("packages");if(!rows.isArray()||rows.isEmpty()||rows.size()>100)throw ApiValues.invalid();var parcels=new ArrayList<Parcel>();for(var row:rows){var fields=Set.of("skuId","quantity","weightKg","lengthCm","widthCm","heightCm");closed(row,fields,fields);parcels.add(new Parcel(ApiValues.uuid(str(row,"skuId")),str(row,"quantity"),str(row,"weightKg"),str(row,"lengthCm"),str(row,"widthCm"),str(row,"heightCm")));}
  return new QuoteInput(ApiValues.uuid(str(node,"commandId")),ApiValues.uuid(str(node,"cartId")),str(node,"market"),str(node,"currency"),address(node.get("shipper")),address(node.get("destination")),parcels,node.has("serviceCode")?str(node,"serviceCode"):null);
 }
 private static Map<String,Object> providerAddress(Address value){var street=new ArrayList<String>();street.add(value.line1());if(value.line2()!=null)street.add(value.line2());var address=new LinkedHashMap<String,Object>();address.put("streetLines",street);address.put("city",value.city());address.put("stateOrProvinceCode",value.region());address.put("countryCode",value.countryCode());if(value.postalCode()!=null)address.put("postalCode",value.postalCode());return Map.of("address",address);}
 /** FedEx Ship limits apply before a rate can fund checkout. Never truncate or transliterate. */
 public static void shippable(Address value){if(value==null)throw rule();for(String part:List.of(value.recipient(),value.phone(),value.line1(),value.city(),value.region(),value.countryCode()))ascii(part);if(value.line2()!=null)ascii(value.line2());if(value.postalCode()!=null)ascii(value.postalCode());if(value.recipient().length()>70||value.line1().length()>35||(value.line2()!=null&&value.line2().length()>35)||value.city().length()>35||(value.postalCode()!=null&&value.postalCode().length()>10))throw rule();}
 private static void ascii(String value){if(!value.matches("[\\x20-\\x7e]+"))throw rule();}
 public static Map<String,Object> shippingParty(Address value){shippable(value);var party=new LinkedHashMap<String,Object>(providerAddress(value));party.put("contact",Map.of("personName",value.recipient(),"phoneNumber",value.phone().substring(1)));return party;}
 /** Round carton dimensions upward to the provider's integer centimetres; the same mapping is used for booking. */
 public static List<Map<String,Object>> packageLines(List<Parcel> parcels){var rows=new ArrayList<Map<String,Object>>();for(var parcel:parcels)rows.add(Map.of("groupPackageCount",1,"weight",Map.of("units","KG","value",new BigDecimal(parcel.weightKg())),"dimensions",Map.of("units","CM","length",dimension(parcel.lengthCm()).setScale(0,RoundingMode.CEILING).intValueExact(),"width",dimension(parcel.widthCm()).setScale(0,RoundingMode.CEILING).intValueExact(),"height",dimension(parcel.heightCm()).setScale(0,RoundingMode.CEILING).intValueExact())));return List.copyOf(rows);}
 public static JsonNode rateRequest(QuoteInput input,String service,String pickup){if(!Set.of("DROPOFF_AT_FEDEX_LOCATION","USE_SCHEDULED_PICKUP","CONTACT_FEDEX_TO_SCHEDULE").contains(pickup))throw ApiValues.invalid();shippable(input.shipper());shippable(input.destination());return EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of("shipper",providerAddress(input.shipper()),"recipient",providerAddress(input.destination()),"serviceType",service(service),"pickupType",pickup,"packagingType","YOUR_PACKAGING","rateRequestType",List.of("ACCOUNT"),"requestedPackageLineItems",packageLines(input.packages()))));}
 public static Quote rate(JsonNode output,String service,Instant expires){
  var replies=output==null?null:output.get("rateReplyDetails");if(replies==null||!replies.isArray()||replies.isEmpty()||replies.size()>100)throw rule();Quote selected=null;
  for(var reply:replies){if(!service.equals(reply.path("serviceType").asString()))continue;var rates=reply.get("ratedShipmentDetails");if(rates==null||!rates.isArray()||rates.isEmpty()||rates.size()>100)throw rule();for(var rate:rates){if(!"ACCOUNT".equals(rate.path("rateType").asString()))continue;if(selected!=null||!"NGN".equals(rate.path("shipmentRateDetail").path("currency").asString()))throw rule();var charge=rate.get("totalNetCharge");if(charge==null||!charge.isNumber())throw rule();String amount;try{amount=money(charge.asString()).toPlainString();}catch(RuntimeException bad){throw rule();}String reference=nullable(rate,"quoteNumber");selected=new Quote("FEDEX",service,"NGN",amount,expires,reference);}}
  if(selected==null)throw rule();return selected;
 }
}
