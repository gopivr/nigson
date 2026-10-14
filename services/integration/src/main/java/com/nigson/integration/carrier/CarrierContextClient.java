package com.nigson.integration.carrier;

import java.net.*;
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
}
