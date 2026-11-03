package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Dellyman protocol port. Keep durable OrderRef and UNKNOWN handling in the owner. */
public final class DellymanAdapter {
 public record Rate(String companyId,String name,BigDecimal amount) {}
 public record Booking(String orderId,String trackingId) {}
 private final ProviderHttp http;private final String authorization,webhookSecret;
 public DellymanAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String apiKey,String webhookSecret){
  if(mode==ProviderHttp.Mode.TEST&&!"https://dev.dellyman.com".equals(endpoint)||mode==ProviderHttp.Mode.LIVE&&!"https://dellyman.com".equals(endpoint))throw new IllegalArgumentException("DELLYMAN_MODE_TARGET_MISMATCH");
  http=new ProviderHttp(environment,mode,endpoint,"dellyman-mock",Set.of("dellyman.com","dev.dellyman.com"),apiKey,webhookSecret);
  authorization="Bearer "+apiKey;this.webhookSecret=webhookSecret;
 }
 private JsonNode call(String method,String operation,Map<String,Object> input,boolean sideEffect){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  var response=http.call(method,"/api/v3.0/"+operation,"application/json",input==null?"":EventCodec.JSON.writeValueAsString(input),authorization,sideEffect);
  try{var body=EventCodec.JSON.readTree(response.body());if(!body.isObject())throw new IllegalArgumentException();
   if(body.has("ResponseCode")&&body.path("ResponseCode").asInt()!=100)throw new ProviderHttp.Failure("DELLYMAN_REQUEST_REJECTED",false,response.status());
   return body;
  }catch(ProviderHttp.Failure known){throw known;}catch(RuntimeException invalid){throw new ProviderHttp.Failure("DELLYMAN_RESPONSE_INVALID",sideEffect,response.status());}
 }
 private static String text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(c->c<32))throw new IllegalArgumentException("DELLYMAN_REQUEST_INVALID");return value;}
 private static String id(String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("DELLYMAN_ID_INVALID");return value;}
 private static BigDecimal amount(JsonNode value){try{String raw=value.asString();if(!raw.matches("[0-9]{1,12}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException();return new BigDecimal(raw).setScale(2);}catch(RuntimeException invalid){throw new ProviderHttp.Failure("DELLYMAN_AMOUNT_INVALID",false,200);}}
 public List<Rate> quotes(String vehicle,LocalDate pickupDate,String pickupWindow,String pickupAddress,String destination){
  if(pickupDate==null)throw new IllegalArgumentException("DELLYMAN_PICKUP_REQUIRED");
  var result=call("POST","GetQuotes",Map.of("PaymentMode","online","Vehicle",text(vehicle,80),"IsInstantDelivery",0,"PickupRequestedTime",text(pickupWindow,80),"IsProductOrder",0,"ProductAmount",List.of(),"PickupRequestedDate",pickupDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),"PickupAddress",text(pickupAddress,500),"DeliveryAddress",List.of(text(destination,500))),false);
  var companies=result.get("Companies");if(result.path("ResponseCode").asInt()!=100||companies==null||!companies.isArray()||companies.size()>100)throw new ProviderHttp.Failure("DELLYMAN_QUOTE_INVALID",false,200);
  var rates=new ArrayList<Rate>();var seen=new HashSet<String>();for(var row:companies){String company=id(row.path("CompanyID").asString());if(!seen.add(company))throw new ProviderHttp.Failure("DELLYMAN_QUOTE_INVALID",false,200);rates.add(new Rate(company,text(row.path("Name").asString(),200),amount(row.path("PayableAmount"))));}return List.copyOf(rates);
 }
 /** The exact stable OrderRef is persisted before this method; never create a replacement on timeout. */
 public Booking book(UUID orderRef,String companyId,String vehicle,String pickupName,String pickupPhone,String pickupAddress,String pickupLandmark,LocalDate pickupDate,String pickupWindow,String deliveryWindow,String timeline,String description,String recipient,String recipientPhone,String destination,String landmark,BigDecimal weightKg){
  if(orderRef==null||orderRef.equals(new UUID(0,0))||pickupDate==null||weightKg==null||weightKg.signum()<=0||weightKg.compareTo(new BigDecimal("1000"))>0||weightKg.scale()>3||!Set.of("sameDay","nextDay","beyondNextDay").contains(timeline))throw new IllegalArgumentException("DELLYMAN_BOOKING_INVALID");
  var body=new LinkedHashMap<String,Object>();body.put("OrderRef",orderRef.toString());body.put("CompanyID",id(companyId));body.put("PaymentMode","online");body.put("Vehicle",text(vehicle,80));body.put("PickUpContactName",text(pickupName,100));body.put("PickUpContactNumber",phone(pickupPhone));body.put("PickUpGooglePlaceAddress",text(pickupAddress,500));body.put("PickUpLandmark",text(pickupLandmark,200));body.put("IsProductOrder",0);body.put("IsInstantDelivery",0);body.put("PickUpRequestedDate",pickupDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));body.put("PickUpRequestedTime",text(pickupWindow,80));body.put("DeliveryRequestedTime",text(deliveryWindow,80));body.put("DeliveryTimeline",timeline);
  body.put("Packages",List.of(Map.of("PackageDescription",text(description,200),"DeliveryContactName",text(recipient,100),"DeliveryContactNumber",phone(recipientPhone),"DeliveryGooglePlaceAddress",text(destination,500),"DeliveryLandmark",text(landmark,200),"PackageWeight",weightKg)));
  var result=call("POST","BookOrder",body,true);
  try{if(result.path("ResponseCode").asInt()!=100)throw new IllegalArgumentException();return new Booking(id(result.path("OrderID").asString()),id(result.path("TrackingID").asString()));}
  catch(RuntimeException invalid){throw new ProviderHttp.Failure("DELLYMAN_BOOKING_UNCONFIRMED",true,200);}
 }
 private static String phone(String value){if(value==null||!value.matches("(?:\\+?234|0)[1-9][0-9]{9}"))throw new IllegalArgumentException("DELLYMAN_PHONE_INVALID");return value;}
 public JsonNode order(String orderId){var result=call("POST","GetOrder",Map.of("OrderID",id(orderId)),false);if(!orderId.equals(result.path("OrderID").asString()))throw new ProviderHttp.Failure("DELLYMAN_ORDER_MISMATCH",false,200);return result;}
 public boolean authenticWebhook(byte[] raw,String signature){if(raw==null||raw.length>262144||signature==null||!signature.matches("[a-fA-F0-9]{64}"))return false;try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return MessageDigest.isEqual(mac.doFinal(raw),HexFormat.of().parseHex(signature));}catch(java.security.GeneralSecurityException invalid){return false;}}
}
