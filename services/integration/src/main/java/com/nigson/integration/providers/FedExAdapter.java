package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Private carrier HTTP boundary. Booking intent/reconciliation are integration-owned. */
public final class FedExAdapter {
 private final ProviderHttp http;private final String clientId,secret,account;
 private String token;private Instant expires=Instant.EPOCH;
 public FedExAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String clientId,String secret,String account) {
  String host=mode==ProviderHttp.Mode.LIVE?"apis.fedex.com":"apis-sandbox.fedex.com";
  http=new ProviderHttp(environment,mode,endpoint,"fedex-mock",Set.of(host),clientId,secret,account);this.clientId=clientId;this.secret=secret;this.account=account;
 }
 private synchronized String token() {
  if(token!=null&&expires.isAfter(Instant.now().plusSeconds(60)))return token;
  var response=http.call("POST","/oauth/token","application/x-www-form-urlencoded",ProviderHttp.form(Map.of("grant_type","client_credentials","client_id",clientId,"client_secret",secret)),null,false);
  try{var data=EventCodec.JSON.readTree(response.body());String value=data.path("access_token").asString();long ttl=data.path("expires_in").asLong();if(value.isBlank()||value.length()>8192||ttl<61||ttl>86400)throw new IllegalArgumentException();token=value;expires=Instant.now().plusSeconds(ttl);return token;}catch(RuntimeException e){throw new IllegalArgumentException("FEDEX_TOKEN_INVALID");}
 }
 private JsonNode call(String method,String path,JsonNode request) {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  var response=http.call(method,path,"application/json",EventCodec.JSON.writeValueAsString(request),"Bearer "+token(),!path.contains("rates")&&!path.contains("track"));
  try{var data=EventCodec.JSON.readTree(response.body());if(!data.path("output").isObject()||data.has("errors"))throw new IllegalArgumentException();return data.get("output");}catch(RuntimeException e){throw new ProviderHttp.Failure("FEDEX_RESPONSE_INVALID",!path.contains("rates")&&!path.contains("track"),response.status());}
 }
 private JsonNode withAccount(JsonNode request) {
  if(request==null||!request.isObject()||!request.path("requestedShipment").isObject())throw new IllegalArgumentException("FEDEX_SHIPMENT_INVALID");
  var copy=((tools.jackson.databind.node.ObjectNode)request).deepCopy();copy.set("accountNumber",EventCodec.JSON.valueToTree(Map.of("value",account)));return copy;
 }
 public JsonNode rates(JsonNode shipment){var data=call("POST","/rate/v1/rates/quotes",withAccount(shipment));if(!data.path("rateReplyDetails").isArray())throw new IllegalArgumentException("FEDEX_RATES_INVALID");return data;}
 public JsonNode book(JsonNode shipment){var data=call("POST","/ship/v1/shipments",withAccount(shipment));if(!data.path("transactionShipments").isArray()||data.path("transactionShipments").isEmpty())throw new ProviderHttp.Failure("FEDEX_BOOKING_UNCERTAIN",true,200);return data;}
 public JsonNode cancel(String tracking){checkTracking(tracking);return call("PUT","/ship/v1/shipments/cancel",EventCodec.JSON.valueToTree(Map.of("accountNumber",Map.of("value",account),"trackingNumber",tracking,"deletionControl","DELETE_ALL_PACKAGES")));}
 public JsonNode track(String tracking){checkTracking(tracking);return call("POST","/track/v1/trackingnumbers",EventCodec.JSON.valueToTree(Map.of("includeDetailedScans",true,"trackingInfo",List.of(Map.of("trackingNumberInfo",Map.of("trackingNumber",tracking))))));}
 private static void checkTracking(String value){if(value==null||!value.matches("[A-Za-z0-9]{10,40}"))throw new IllegalArgumentException("FEDEX_TRACKING_INVALID");}
}
