package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Private carrier HTTP boundary. Booking intent/reconciliation are integration-owned. */
public final class FedExAdapter implements CarrierProvider {
 private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(14*1024*1024).maxNumberLength(40).build()).enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(tools.jackson.databind.cfg.JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).disable(tools.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES).build();
 private final ProviderHttp http;private final String clientId,secret,account;private final ProviderHttp.Mode mode;
 private String token;private Instant expires=Instant.EPOCH;
 public FedExAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String clientId,String secret,String account) {
  if(mode!=ProviderHttp.Mode.MOCK)for(String credential:List.of(Objects.requireNonNullElse(clientId,""),Objects.requireNonNullElse(secret,""),Objects.requireNonNullElse(account,"")))if(credential.toLowerCase(Locale.ROOT).matches(".*(synthetic|fixture|configuration_validation).*"))throw new IllegalArgumentException("PROVIDER_CREDENTIAL_MODE_INVALID");
  String host=mode==ProviderHttp.Mode.LIVE?"apis.fedex.com":"apis-sandbox.fedex.com";
  http=new ProviderHttp(environment,mode,endpoint,"fedex-mock",Set.of(host),clientId,secret,account);this.clientId=clientId;this.secret=secret;this.account=account;this.mode=mode;
 }
 public ProviderHttp.Mode mode(){return mode;}
 public String accountFingerprint(){return EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("provider","FEDEX","mode",mode.name(),"account",account)));}
 private synchronized String token() {
  if(token!=null&&expires.isAfter(Instant.now().plusSeconds(60)))return token;
  var response=http.call("POST","/oauth/token","application/x-www-form-urlencoded",ProviderHttp.form(Map.of("grant_type","client_credentials","client_id",clientId,"client_secret",secret)),null,false);
  try{var data=JSON.readTree(response.body());var access=data.get("access_token");var life=data.get("expires_in");if(access==null||!access.isString()||life==null||!life.isIntegralNumber()||!life.canConvertToLong()||!data.path("token_type").isString()||!data.path("token_type").asString().equalsIgnoreCase("bearer"))throw new IllegalArgumentException();String value=access.asString();long ttl=life.asLong();if(!value.matches("[A-Za-z0-9._~+/-]{1,8192}={0,2}")||ttl<61||ttl>86400)throw new IllegalArgumentException();token=value;expires=Instant.now().plusSeconds(ttl);return token;}catch(RuntimeException e){throw new ProviderHttp.Failure("FEDEX_TOKEN_INVALID",false,response.status());}
 }
 private synchronized void invalidate(String rejected){if(Objects.equals(token,rejected)){token=null;expires=Instant.EPOCH;}}
 private JsonNode call(String method,String path,JsonNode request) {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  boolean effect=path.equals("/ship/v1/shipments")||path.equals("/ship/v1/shipments/cancel");String bearer=token();int limit=path.equals("/ship/v1/shipments")||path.equals("/ship/v1/shipments/results")?16*1024*1024:1048576;ProviderHttp.Result response;
  try{response=http.call(method,path,"application/json",EventCodec.JSON.writeValueAsString(request),"Bearer "+bearer,effect,limit);}catch(ProviderHttp.Failure failure){
   if(failure.status!=401)throw failure;invalidate(bearer);
   // Only read operations may renew and repeat in this call; writes return to their durable owner.
   if(effect)throw failure;
   response=http.call(method,path,"application/json",EventCodec.JSON.writeValueAsString(request),"Bearer "+token(),false,limit);
  }
  try{var data=JSON.readTree(response.body());if(!data.path("output").isObject()||data.has("errors"))throw new IllegalArgumentException();return data.get("output");}catch(RuntimeException e){throw new ProviderHttp.Failure("FEDEX_RESPONSE_INVALID",effect,response.status());}
 }
 private JsonNode withAccount(JsonNode request) {
  if(request==null||!request.isObject()||!request.path("requestedShipment").isObject())throw new IllegalArgumentException("FEDEX_SHIPMENT_INVALID");
  var copy=((tools.jackson.databind.node.ObjectNode)request).deepCopy();copy.set("accountNumber",EventCodec.JSON.valueToTree(Map.of("value",account)));return copy;
 }
 public JsonNode rates(JsonNode shipment){var data=call("POST","/rate/v1/rates/quotes",withAccount(shipment));if(!data.path("rateReplyDetails").isArray())throw new IllegalArgumentException("FEDEX_RATES_INVALID");return data;}
 public JsonNode book(JsonNode shipment){var data=call("POST","/ship/v1/shipments",withAccount(shipment));if(!completed(data)&&!validJob(data.path("jobId")))throw new ProviderHttp.Failure("FEDEX_BOOKING_UNCERTAIN",true,200);return data;}
 public JsonNode confirmed(String jobId){if(jobId==null||!jobId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,159}"))throw new IllegalArgumentException("FEDEX_JOB_INVALID");var data=call("POST","/ship/v1/shipments/results",EventCodec.JSON.valueToTree(Map.of("accountNumber",Map.of("value",account),"jobId",jobId)));if(!completed(data))throw new ProviderHttp.Failure("FEDEX_RESULTS_UNAVAILABLE",false,200);return data;}
 private static boolean completed(JsonNode data){return data.path("transactionShipments").isArray()&&!data.path("transactionShipments").isEmpty();}
 private static boolean validJob(JsonNode job){return job.isString()&&job.asString().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,159}");}
 public JsonNode cancel(String tracking){checkTracking(tracking);return call("PUT","/ship/v1/shipments/cancel",EventCodec.JSON.valueToTree(Map.of("accountNumber",Map.of("value",account),"trackingNumber",tracking,"deletionControl","DELETE_ALL_PACKAGES")));}
 public JsonNode track(String tracking){checkTracking(tracking);return call("POST","/track/v1/trackingnumbers",EventCodec.JSON.valueToTree(Map.of("includeDetailedScans",true,"trackingInfo",List.of(Map.of("trackingNumberInfo",Map.of("trackingNumber",tracking))))));}
 public JsonNode trackReference(String reference,LocalDate begin,LocalDate end){
  if(reference==null||!reference.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}")||begin==null||end==null||end.isBefore(begin)||java.time.temporal.ChronoUnit.DAYS.between(begin,end)>90)throw new IllegalArgumentException("FEDEX_REFERENCE_INVALID");
  return call("POST","/track/v1/referencenumbers",EventCodec.JSON.valueToTree(Map.of("includeDetailedScans",true,"referencesInformation",Map.of("type","CUSTOMER_REFERENCE","value",reference,"accountNumber",account,"shipDateBegin",begin.toString(),"shipDateEnd",end.toString()))));
 }
 private static void checkTracking(String value){if(value==null||!value.matches("[A-Za-z0-9]{10,40}"))throw new IllegalArgumentException("FEDEX_TRACKING_INVALID");}
}
