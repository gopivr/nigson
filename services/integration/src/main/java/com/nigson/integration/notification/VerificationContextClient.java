package com.nigson.integration.notification;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.integration.provider.PrivateHttp;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

/** Fixed scoped source read. Proof authorization is API187, never a transactional consent override. */
public final class VerificationContextClient {
 public record Context(String phone,String channel,String code,Instant expiresAt){@Override public String toString(){return "VerificationContext[protected]";}}
 private static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-187")&&r.client().equals("nigson-integration")&&r.owner().equals("experience")).findFirst().orElseThrow();
 private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(1024).maxNumberLength(40).build()).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final URI base;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;private final Clock clock;
 public VerificationContextClient(String environment,String endpoint,Function<PrivateWorkload.Route,String> credentials,Clock clock){
  if(!Set.of("local","test","dev","qa","stage","prod").contains(environment)||endpoint==null||endpoint.length()>2048)throw new IllegalArgumentException("MESSAGE_SOURCE_CONFIGURATION_REQUIRED");var uri=URI.create(endpoint);
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("PRIVATE_TARGET_REQUIRED");
  if(Set.of("local","test").contains(environment)){if(!"http".equals(uri.getScheme())||!Set.of("experience","127.0.0.1","localhost").contains(uri.getHost()))throw new IllegalArgumentException("LOCAL_PRIVATE_TARGET_REQUIRED");}
  else if(!"https".equals(uri.getScheme())||!uri.getHost().endsWith(".internal")||uri.getPort()!=-1)throw new IllegalArgumentException("APPROVED_PRIVATE_HTTPS_REQUIRED");
  base=uri;this.credentials=Objects.requireNonNull(credentials);this.clock=Objects.requireNonNull(clock);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 }
 public Context read(UUID verification,UUID job,String channel,UUID correlation){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");for(UUID id:Arrays.asList(verification,job,correlation))if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();if(!VerificationRequestedHandler.job(verification).equals(job)||!Set.of("SMS","WHATSAPP").contains(channel))throw ApiValues.invalid();
  try{String token=credentials.apply(ROUTE);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw unavailable();var request=HttpRequest.newBuilder(base.resolve(ROUTE.path().replace("{id}",verification.toString()))).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("X-Correlation-ID",correlation.toString()).POST(HttpRequest.BodyPublishers.ofByteArray(EventCodec.JSON.writeValueAsBytes(Map.of("jobId",job)))).build();var response=PrivateHttp.send(http,request,4096,Duration.ofSeconds(6));
   if(response.statusCode()!=200)throw new Problem.Failure(switch(response.statusCode()){case 401,403->ProblemCode.ACCESS_DENIED;case 404->ProblemCode.NOT_FOUND;case 409,422->ProblemCode.CONSENT_RULE_VIOLATION;default->ProblemCode.PROVIDER_UNAVAILABLE;});if(response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))throw unavailable();var body=JSON.readTree(response.body());if(!body.isObject()||!body.propertyNames().equals(Set.of("phone","channel","code","expiresAt","templateCode"))||!body.path("phone").isString()||!body.path("phone").asString().matches("\\+[1-9][0-9]{6,14}")||!body.path("channel").asString().equals(channel)||!body.path("code").isString()||!body.path("code").asString().matches("[0-9]{6}")||!"CONTACT_VERIFICATION".equals(body.path("templateCode").asString())||!body.path("expiresAt").isString())throw unavailable();Instant expiry=Instant.parse(body.path("expiresAt").asString());if(!expiry.isAfter(clock.instant())||expiry.isAfter(clock.instant().plusSeconds(600)))throw new Problem.Failure(ProblemCode.CONSENT_RULE_VIOLATION);return new Context(body.path("phone").asString(),channel,body.path("code").asString(),expiry);
  }catch(Problem.Failure safe){throw safe;}catch(RuntimeException invalid){throw unavailable();}
 }
 private static Problem.Failure unavailable(){return new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
}
