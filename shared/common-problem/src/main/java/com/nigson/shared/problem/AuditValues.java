package com.nigson.shared.problem;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
/** Redaction/value policy only. Owned writers enforce transaction and append-only grants. */
public final class AuditValues {
 private static final JsonMapper JSON=new JsonMapper();
 private static final Set<String> STATES;
 static {try(var input=AuditValues.class.getResourceAsStream("/audit/states.json")){var states=new HashSet<String>();for(var v:JSON.readTree(input))states.add(v.asString());STATES=Set.copyOf(states);}catch(Exception missing){throw new ExceptionInInitializerError("AUDIT_POLICY_UNAVAILABLE");}}
 public enum ActorKind { USER,SERVICE,SYSTEM }
 public enum Reason { OPERATOR_REQUEST,VERIFIED_PROVIDER,RECONCILIATION,PUBLICATION_REVIEW,SECURITY_POLICY,CONFIGURATION_CHANGE }
 public enum Action { PAYMENT_RECEIPT, REFUND_SUBMISSION, ORDER_CHECKOUT,ORDER_PROCESSING,ORDER_CANCELLATION,ORDER_SETTLEMENT,PAYMENT_INITIATION,PAYMENT_VERIFICATION, SHIPMENT_CREATE,SHIPMENT_DISPATCH,SHIPMENT_DELIVERY,STOCK_RETURN, DELIVERY_RULE_CREATE,DELIVERY_RULE_UPDATE, CART_CREATE,CART_SET,CART_REMOVE,CART_TRANSITION, PRICE_CREATE,PRICE_REVISION,CHECKOUT_QUOTE, MARKET_CREATE,MARKET_BOOK,MARKET_ELIGIBILITY, STOCK_TRANSITION, STOCK_LOCATION_CREATE, NOTIFICATION_DELIVERY,NOTIFICATION_TEMPLATE, FILE_UPLOAD,FILE_COMPLETION,FILE_SCAN_RESULT,FILE_PROMOTION_REQUEST,FILE_TRANSFER_UNKNOWN, PRODUCT_CREATE,PRODUCT_UPDATE,SKU_CREATE,SKU_UPDATE,TAXONOMY_CREATE,TAXONOMY_UPDATE, SESSION_LOGIN,SESSION_LOGOUT, PROFILE_UPDATE,ADDRESS_CREATE,ADDRESS_UPDATE,ADDRESS_DELETE,PARTY_PROVISION,STAFF_ROLE_GRANT,STAFF_ROLE_REVOKE,ENTITLEMENT_APPROVAL,ENTITLEMENT_SUSPENSION,PRICE_ACTIVATION,PROMOTION_ACTIVATION,STOCK_ADJUSTMENT,REFUND_REQUEST,REFUND_APPROVAL,REFUND_VERIFICATION,QUOTE_OVERRIDE,CONTENT_PUBLICATION,CONTENT_WITHDRAWAL,MODERATION,PRIVATE_FILE_DOWNLOAD,APPLICANT_EXPORT,FEATURE_ACTIVATION,QUEUE_REPLAY,AI_POLICY_ACTIVATION }
 public enum Resource { PAYMENT_RECEIPT, SHIPMENT,ORDER, DELIVERY_RULE, CART, MARKET, NOTIFICATION, PRODUCT,SKU,BRAND,CATEGORY, SESSION, PARTY,PROFILE,ADDRESS,STAFF_ROLE,ENTITLEMENT,PRICE_BOOK,PROMOTION,INVENTORY,REFUND,QUOTE,CONTENT,REVIEW,FILE,CAREER_APPLICATION,FEATURE,QUEUE,AI_POLICY }
 private AuditValues(){}
 public static String subject(String value){if(value==null||value.isBlank()||value.length()>200||value.chars().anyMatch(c->c<32||c>126))throw ApiValues.invalid();return value;}
 public static JsonNode redact(Map<String,?> values){
  if(values==null)return null;if(values.size()>100)throw ApiValues.invalid();var out=JSON.createObjectNode();boolean redacted=false;
  for(var e:values.entrySet()){
   String key=e.getKey();Object value=e.getValue();switch(key){
    case "version","revision" -> {var node=JSON.valueToTree(value);if(!node.isIntegralNumber()||!node.canConvertToLong()||node.asLong()<0)throw ApiValues.invalid();out.set(key,node);}
    case "enabled" -> {if(!(value instanceof Boolean b))throw ApiValues.invalid();out.put(key,b);}
    case "amount" -> {if(!(value instanceof String text))throw ApiValues.invalid();ApiValues.money(text,false);out.put(key,text);}
    case "quantity" -> {if(!(value instanceof String text))throw ApiValues.invalid();ApiValues.quantity(text);out.put(key,text);}
    case "currency" -> {if(!(value instanceof String text)||!text.matches("[A-Z]{3}"))throw ApiValues.invalid();try{Currency.getInstance(text);}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}out.put(key,text);}
    case "state","role","channel","providerMode" -> {if(!(value instanceof String text)||!switch(key){case "state"->STATES.contains(text);case "role"->Set.of("CUSTOMER","CONTENT_EDITOR","CONTENT_PUBLISHER","SALES","OPERATIONS","FINANCE","ANALYST","ADMIN","SERVICE_EXPERIENCE","SERVICE_INTEGRATION","SERVICE_AI","SERVICE_COMMERCE","OWNER","BUYER").contains(text);case "channel"->Set.of("EMAIL","SMS","WHATSAPP").contains(text);default->Set.of("MOCK","TEST","LIVE").contains(text);})throw ApiValues.invalid();out.put(key,text);}
    default -> redacted=true;
   }
  }
  if(redacted)out.put("redacted",true);if(JSON.writeValueAsBytes(out).length>16384)throw ApiValues.invalid();return out;
 }
 public static String ipFingerprint(byte[] key,String address){
  if(key==null||key.length<32||address==null||address.length()>45||!address.matches("[0-9a-fA-F:.]+")||(!address.contains(":")&&!address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))throw ApiValues.invalid();
  try{byte[] normalized=java.net.InetAddress.getByName(address).getAddress();var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(normalized));}catch(Exception invalid){throw ApiValues.invalid();}
 }
}
