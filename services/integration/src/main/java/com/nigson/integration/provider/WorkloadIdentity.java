package com.nigson.integration.provider;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.oauth2.jwt.*;
import com.nigson.shared.security.*;

/** Integration-specific client credentials; tokens never come from a browser or stored job. */
public final class WorkloadIdentity {
 private final String issuer,secret;private final Clock clock;private final HttpClient http;private final Map<String,JwtDecoder> decoders;private final Map<String,Jwt> tokens=new HashMap<>();
 public WorkloadIdentity(String env,String issuer,String secret,int phase,Clock clock){
  if(secret==null||secret.length()<32||secret.length()>512||phase<1||phase>5||secret.chars().anyMatch(c->c<32)||!Set.of("local","test").contains(env)&&secret.toLowerCase(Locale.ROOT).matches(".*(mock|dummy|placeholder|synthetic|configuration_validation).*"))throw new IllegalArgumentException("WORKLOAD_CREDENTIAL_POLICY_REQUIRED");
  new KeycloakIdentity.Policy(env,issuer,issuer+"/protocol/openid-connect/certs","nigson-integration",Set.of("nigson-integration"),Set.of("validation-only"));this.issuer=issuer;this.secret=secret;this.clock=clock;http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
  var values=new HashMap<String,JwtDecoder>();PrivateWorkload.ROUTES.stream().filter(r->r.client().equals("nigson-integration")&&r.phase()<=phase).forEach(r->values.put(r.api(),KeycloakIdentity.decoder(new KeycloakIdentity.Policy(env,issuer,issuer+"/protocol/openid-connect/certs","nigson-"+r.owner(),Set.of("nigson-integration"),Set.of(r.scope())),clock,null)));decoders=Map.copyOf(values);
 }
 public synchronized String token(PrivateWorkload.Route route){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");if(!route.client().equals("nigson-integration")||!decoders.containsKey(route.api())||!PrivateWorkload.ROUTES.contains(route))throw ActorAssertion.denied();var cached=tokens.get(route.api());if(cached!=null&&cached.getExpiresAt().isAfter(clock.instant().plusSeconds(30)))return cached.getTokenValue();
  try{
   String form=ProviderHttp.form(Map.of("grant_type","client_credentials","client_id","nigson-integration","client_secret",secret));var response=PrivateHttp.send(http,HttpRequest.newBuilder(URI.create(issuer+"/protocol/openid-connect/token")).timeout(Duration.ofSeconds(5)).header("Content-Type","application/x-www-form-urlencoded").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(form)).build(),65536,Duration.ofSeconds(6));
   if(response.statusCode()!=200||response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))throw ActorAssertion.denied();
   var json=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(16384).maxNumberLength(20).build()).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(response.body());if(!json.path("token_type").asString().equals("Bearer")||!json.path("access_token").isString()||json.path("access_token").asString().length()>16384)throw ActorAssertion.denied();Jwt token=decoders.get(route.api()).decode(json.path("access_token").asString());tokens.put(route.api(),token);return token.getTokenValue();
  }catch(RuntimeException unavailable){tokens.remove(route.api());throw new IllegalStateException("WORKLOAD_IDENTITY_UNAVAILABLE");}
 }
}
