package com.nigson.commerce.payment.internal.infrastructure.external.paystack;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

/** HTTP boundary only. Financial effects and UNKNOWN reconciliation belong to commerce commands. */
public final class PaystackAdapter {
 private final ProviderHttp http;private final String key;private final ProviderHttp.Mode mode;
 public PaystackAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String key){
  if(key==null)throw new IllegalArgumentException("PAYSTACK_KEY_MISSING");
  if(mode!=ProviderHttp.Mode.MOCK&&(key.toLowerCase(Locale.ROOT).matches(".*(synthetic|fixture|configuration_validation|shape_only|changeme|your_secret|test_key).*" )||key.matches("sk_(test|live)_0+")))throw new IllegalArgumentException("PAYSTACK_SYNTHETIC_KEY_DENIED");
  if(mode==ProviderHttp.Mode.MOCK&&!key.equals("sk_mock_nigson")||mode==ProviderHttp.Mode.TEST&&!key.startsWith("sk_test_")||mode==ProviderHttp.Mode.LIVE&&!key.startsWith("sk_live_"))throw new IllegalArgumentException("PAYSTACK_KEY_MODE_INVALID");
  http=new ProviderHttp(environment,mode,endpoint,"paystack-mock",Set.of("api.paystack.co"),key);this.key=key;this.mode=mode;
 }
 public static long kobo(String amount) {
  if(amount==null||!amount.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("NGN_AMOUNT_INVALID");
  try{long result=new BigDecimal(amount).movePointRight(2).longValueExact();if(result<=0)throw new IllegalArgumentException("NGN_AMOUNT_INVALID");return result;}catch(ArithmeticException e){throw new IllegalArgumentException("NGN_AMOUNT_INVALID");}
 }
 private JsonNode call(String method,String path,Map<String,Object> body) {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  var response=http.call(method,path,"application/json",body==null?"":EventCodec.JSON.writeValueAsString(body),"Bearer "+key);
  try {var json=EventCodec.JSON.readTree(response.body());if(!json.path("status").asBoolean()||json.path("data").isMissingNode())throw new IllegalArgumentException("PAYSTACK_RESPONSE_INVALID");return json.get("data");}catch(RuntimeException e){throw new ProviderHttp.Failure("PAYSTACK_RESPONSE_INVALID",!method.equals("GET"),response.status());}
 }
 private static String reference(String ref){if(ref==null||!ref.matches("[a-zA-Z0-9.=_-]{1,100}"))throw new IllegalArgumentException("PAYSTACK_REFERENCE_INVALID");return ref;}
 public JsonNode initialize(String ref,String email,String amount){
  reference(ref);if(email==null||email.length()>254||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("PAYSTACK_EMAIL_INVALID");
  var data=call("POST","/transaction/initialize",Map.of("reference",ref,"email",email,"amount",kobo(amount),"currency","NGN"));
  if(!data.path("reference").asString().equals(ref)||!data.path("authorization_url").isString()||!data.path("access_code").isString())throw new ProviderHttp.Failure("PAYSTACK_INITIALIZATION_UNCERTAIN",true,200);
  java.net.URI hosted=java.net.URI.create(data.path("authorization_url").asString());
  if(hosted.getUserInfo()!=null||hosted.getHost()==null||(mode==ProviderHttp.Mode.MOCK?!(hosted.getScheme().equals("http")&&hosted.getHost().equals("paystack-mock")):!(hosted.getScheme().equals("https")&&hosted.getHost().equals("checkout.paystack.com"))))throw new ProviderHttp.Failure("PAYSTACK_HOSTED_URL_UNCERTAIN",true,200);return data;
 }
 public JsonNode verify(String ref,long expectedKobo){
  var data=call("GET","/transaction/verify/"+reference(ref),null);
  if(expectedKobo<=0||!data.path("domain").asString().equals(mode==ProviderHttp.Mode.LIVE?"live":"test")||!data.path("amount").isIntegralNumber()||data.path("amount").asLong()!=expectedKobo||!data.path("currency").asString().equals("NGN")||!data.path("reference").asString().equals(ref)||!Set.of("abandoned","failed","ongoing","pending","processing","queued","reversed","success").contains(data.path("status").asString()))throw new IllegalArgumentException("PAYSTACK_VERIFICATION_MISMATCH");return data;
 }
 public JsonNode refund(String ref,String amount){return call("POST","/refund",Map.of("transaction",reference(ref),"amount",kobo(amount)));}
 public JsonNode refund(String ref,String amount,String businessReference){if(businessReference==null||!businessReference.matches("[A-Za-z0-9_-]{1,120}"))throw new IllegalArgumentException("REFUND_REFERENCE_INVALID");return call("POST","/refund",Map.of("transaction",reference(ref),"amount",kobo(amount),"currency","NGN","merchant_note",businessReference));}
 public JsonNode refundStatus(String id){if(id==null||!id.matches("[a-zA-Z0-9_-]{1,100}"))throw new IllegalArgumentException("REFUND_ID_INVALID");return call("GET","/refund/"+id,null);}
 public JsonNode refunds(){return call("GET","/refund",null);}
 public JsonNode refunds(String transactionId,int page){if(transactionId==null||!transactionId.matches("[A-Za-z0-9_-]{1,120}")||page<1||page>10)throw new IllegalArgumentException("REFUND_LOOKUP_INVALID");return call("GET","/refund?transaction="+transactionId+"&currency=NGN&perPage=100&page="+page,null);}
 public String webhookDigest(byte[] canonical){if(canonical==null||canonical.length>1048576)throw new IllegalArgumentException("PAYSTACK_RECEIPT_DIGEST_INVALID");try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(canonical));}catch(java.security.GeneralSecurityException failure){throw new IllegalStateException("PAYSTACK_RECEIPT_DIGEST_UNAVAILABLE");}}
 public boolean authenticWebhook(byte[] raw,String signature){return com.nigson.shared.security.CallbackSignatures.paystack(raw,signature,key);}
}
