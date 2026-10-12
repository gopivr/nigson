package com.nigson.integration.provider;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;

/** Calls only commerce's compiled owned reconciliation command; never holds payment keys. */
public final class PaymentReconciliationHandler implements JobHandler {
 private static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-118")&&r.client().equals("nigson-integration")&&r.owner().equals("commerce")).findFirst().orElseThrow();
 private final URI target;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;private final int limit;
 public PaymentReconciliationHandler(String env,String endpoint,int limit,Function<PrivateWorkload.Route,String> credentials){
  if(!Set.of("local","test","dev","qa","stage","prod").contains(env)||limit<1||limit>100||endpoint==null||endpoint.length()>2048)throw new IllegalArgumentException("PAYMENT_RECONCILIATION_CONFIG_REQUIRED");URI uri=URI.create(endpoint);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("PRIVATE_TARGET_REQUIRED");
  if(Set.of("local","test").contains(env)){if(!uri.getScheme().equals("http")||!Set.of("commerce","127.0.0.1","localhost").contains(uri.getHost()))throw new IllegalArgumentException("LOCAL_PRIVATE_TARGET_REQUIRED");}
  else if(!uri.getScheme().equals("https")||!uri.getHost().endsWith(".internal")||uri.getPort()!=-1)throw new IllegalArgumentException("APPROVED_PRIVATE_HTTPS_REQUIRED");
  target=uri.resolve(ROUTE.path());this.limit=limit;this.credentials=Objects.requireNonNull(credentials);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 }
 public JobValues.Kind kind(){return JobValues.Kind.PAYMENT_RECONCILE;}public Provider provider(){return Provider.COMMERCE_PAYMENT;}
 public Outcome execute(JobExecution.Claim claim){return command(claim);}public Outcome reconcile(JobExecution.Claim claim){return command(claim);}
 private Outcome command(JobExecution.Claim claim){
  if(claim.job().kind()!=kind()||!claim.job().owner().equals("commerce"))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  try{
   String token=credentials.apply(ROUTE);if(token==null||token.isBlank()||token.length()>16384||token.chars().anyMatch(c->c<32))throw new IllegalArgumentException("WORKLOAD_REQUIRED");var body=EventCodec.JSON.createObjectNode().put("jobId",claim.job().id().toString()).put("limit",limit);if(claim.nextCursor()!=null)body.put("cursor",claim.nextCursor());
   var request=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("Idempotency-Key",claim.commandKey()).header("X-Correlation-ID",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofByteArray(EventCodec.JSON.writeValueAsBytes(body))).build();var response=PrivateHttp.send(http,request,65536,Duration.ofSeconds(6));
   if(response.statusCode()!=202||response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))return new Outcome(JobValues.State.UNKNOWN,null,ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
   var value=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(65536).maxNumberLength(20).build()).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(response.body());if(!value.isObject()||value.size()!=3||!Set.of("processed","nextCursor","mismatches").containsAll(value.propertyNames()))throw ApiValues.invalid();for(String name:List.of("processed","mismatches"))if(!value.path(name).isIntegralNumber()||!value.path(name).canConvertToInt()||value.path(name).asInt()<0||value.path(name).asInt()>limit)throw ApiValues.invalid();if(value.path("mismatches").asInt()>value.path("processed").asInt())throw ApiValues.invalid();
   var next=value.get("nextCursor");if(!next.isNull()&&(!next.isString()||!next.asString().matches("[A-Za-z0-9_-]{1,256}")||next.asString().equals(claim.nextCursor())))throw ApiValues.invalid();return next.isNull()?new Outcome(JobValues.State.SUCCEEDED,null,null):new Outcome(JobValues.State.PENDING,next.asString(),null);
  }catch(RuntimeException unavailable){return new Outcome(JobValues.State.UNKNOWN,null,ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
 }
}
