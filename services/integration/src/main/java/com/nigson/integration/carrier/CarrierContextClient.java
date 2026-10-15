package com.nigson.integration.carrier;

import java.net.*;
import tools.jackson.databind.JsonNode;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.integration.provider.PrivateHttp;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Fixed scoped read of source-owned paid/frozen creation context; never creates or retries a booking. */
public final class CarrierContextClient {
 public record Acknowledgement(String status,long version){}
 private static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-119")&&r.client().equals("nigson-integration")&&r.owner().equals("commerce")).findFirst().orElseThrow();
 private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(65536).maxNumberLength(40).build()).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final URI base;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;
 public CarrierContextClient(String env,String endpoint,Function<PrivateWorkload.Route,String> credentials){
  if(env==null||!Set.of("local","test","dev","qa","stage","prod").contains(env)||endpoint==null||endpoint.length()>2048)throw new IllegalArgumentException("CARRIER_CONTEXT_CONFIG_REQUIRED");
  URI uri;try{uri=URI.create(endpoint);}catch(IllegalArgumentException malformed){throw new IllegalArgumentException("PRIVATE_TARGET_REQUIRED");}
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("PRIVATE_TARGET_REQUIRED");
  if(Set.of("local","test").contains(env)){if(!"http".equals(uri.getScheme())||!Set.of("commerce","127.0.0.1","localhost").contains(uri.getHost()))throw new IllegalArgumentException("LOCAL_PRIVATE_TARGET_REQUIRED");}
  else if(!"https".equals(uri.getScheme())||!uri.getHost().endsWith(".internal")||uri.getPort()!=-1)throw new IllegalArgumentException("APPROVED_PRIVATE_HTTPS_REQUIRED");
  base=uri;this.credentials=Objects.requireNonNull(credentials);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 }
 public CarrierCreationContext read(UUID shipment,UUID reference,ProviderHttp.Mode mode,UUID correlation){
  for(UUID id:Arrays.asList(shipment,reference,correlation))if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();if(mode==null)throw ApiValues.invalid();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  try{
   String token=credentials.apply(ROUTE);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
   var request=HttpRequest.newBuilder(base.resolve(ROUTE.path().replace("{id}",shipment.toString()))).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Accept","application/json").header("X-Correlation-ID",correlation.toString()).GET().build();var response=PrivateHttp.send(http,request,65536,Duration.ofSeconds(6));
   int status=response.statusCode();if(status!=200){throw new Problem.Failure(switch(status){case 404->ProblemCode.NOT_FOUND;case 409->ProblemCode.INVALID_TRANSITION;case 422->ProblemCode.PROVIDER_RULE_VIOLATION;case 401,403->ProblemCode.ACCESS_DENIED;default->ProblemCode.PROVIDER_UNAVAILABLE;});}
   if(response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))throw CarrierValues.rule();return CarrierCreationContext.parse(JSON.readTree(response.body()),shipment,reference,mode);
  }catch(Problem.Failure safe){throw safe;}catch(RuntimeException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
 }
 private static byte[] ackBytes(JsonNode body){var sorted=new TreeMap<String,JsonNode>();body.properties().forEach(entry->{if(!entry.getValue().isString())throw CarrierValues.rule();sorted.put(entry.getKey(),entry.getValue());});return com.nigson.shared.events.EventCodec.JSON.writeValueAsBytes(sorted);}
 private static boolean validAcknowledgement(JsonNode body){if(!"FEDEX".equals(body.path("provider").asString()))return false;if(body.propertyNames().equals(Set.of("commandId","provider","reference","status","occurredAt","mode")))return Set.of("SHIPPED","DELIVERED","CANCELLED","EXCEPTION").contains(body.path("status").asString());return body.propertyNames().equals(Set.of("commandId","provider","reference","status","occurredAt","labelObjectKey","labelChecksum","bookingState","mode"))&&"READY".equals(body.path("status").asString())&&"BOOKED".equals(body.path("bookingState").asString());}
 public Acknowledgement acknowledge(UUID shipment,JsonNode body,UUID correlation){
  for(UUID id:Arrays.asList(shipment,correlation))if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-120")&&r.client().equals("nigson-integration")&&r.owner().equals("commerce")).findFirst().orElseThrow();
  try{if(body==null||!body.isObject()||!validAcknowledgement(body))throw CarrierValues.rule();String token=credentials.apply(route);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);var request=HttpRequest.newBuilder(base.resolve(route.path().replace("{id}",shipment.toString()))).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Accept","application/json").header("Content-Type","application/json").header("X-Correlation-ID",correlation.toString()).header("Idempotency-Key",ApiValues.uuid(body.path("commandId").asString()).toString()).POST(HttpRequest.BodyPublishers.ofByteArray(ackBytes(body))).build();var response=PrivateHttp.send(http,request,65536,Duration.ofSeconds(6));if(response.statusCode()!=200||response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);var value=JSON.readTree(response.body());if(!value.isObject()||!value.propertyNames().equals(Set.of("id","orderId","status","lines","trackingUrl","version"))||!shipment.equals(ApiValues.uuid(value.path("id").asString()))||!value.path("version").isIntegralNumber()||!value.path("version").canConvertToLong()||value.path("version").asLong()<0||!value.path("status").isString()||!Set.of("CREATED","READY","SHIPPED","DELIVERED","CANCELLED","EXCEPTION").contains(value.path("status").asString())||!value.path("lines").isArray()||value.path("lines").size()>100)throw CarrierValues.rule();ApiValues.uuid(value.path("orderId").asString());for(var line:value.path("lines")){if(!line.isObject()||!line.propertyNames().equals(Set.of("orderLineId","quantity"))||!line.path("quantity").isString())throw CarrierValues.rule();ApiValues.uuid(line.path("orderLineId").asString());ApiValues.quantity(line.path("quantity").asString());}if(!value.path("trackingUrl").isNull()&&!value.path("trackingUrl").isString())throw CarrierValues.rule();return new Acknowledgement(value.path("status").asString(),value.path("version").asLong());}catch(Problem.Failure safe){throw safe;}catch(RuntimeException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
 }
}
