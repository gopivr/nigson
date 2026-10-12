package com.nigson.acceptance.provider;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.integration.IntegrationApplication;
import com.nigson.integration.provider.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.*;
import static org.assertj.core.api.Assertions.*;

class IntegrationJobHttpTest {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private ECKey workload,staff;private String issuer,base;private UUID job,party=UUID.randomUUID();
 private String token(String client,String scope)throws Exception{return token(client,scope,"nigson-integration");}
 private String token(String client,String scope,String audience)throws Exception{
  Instant now=Instant.now();var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(workload.getKeyID()).type(JOSEObjectType.JWT).build(),new JWTClaimsSet.Builder().issuer(issuer).subject("synthetic-integration-workload").audience(audience).claim("azp",client).claim("client_id",client).claim("typ","Bearer").claim("scope",scope).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(120))).build());jwt.sign(new ECDSASigner(workload));return jwt.serialize();
 }
 private String actor(String path,String body,UUID correlation,Set<String> roles,Instant mfa)throws Exception{
  var actor=new ActorAssertion.Actor(party,issuer,"synthetic-operator",null,roles,Set.of("jobs.retry_WITH_SIGNED_STAFF"),mfa);var binding=new ActorAssertion.Binding("integration","jobs.retry_WITH_SIGNED_STAFF","POST",path,correlation,ActorAssertion.Binding.bodyHash(body.getBytes(StandardCharsets.UTF_8)));
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(staff.getKeyID()).type(new JOSEObjectType("nigson-actor+jwt")).build(),ActorAssertion.claims(actor,binding,Instant.now()));jwt.sign(new ECDSASigner(staff));return jwt.serialize();
 }
 private HttpResponse<String> get(String token,String query)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(base+"/internal/v1/jobs"+query)).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());}
 private HttpResponse<String> retry(String token,String signed,String body,UUID correlation,String key,String version)throws Exception{
  var request=HttpRequest.newBuilder(URI.create(base+"/internal/v1/jobs/"+job+"/retry")).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("X-Correlation-ID",correlation.toString()).header("Idempotency-Key",key).header("If-Match",version);if(signed!=null)request.header("X-Nigson-Actor",signed);
  return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void actualPrivateHttpValidatesJwtSignedBodyMfaVersionReplayAndClosedViews()throws Exception{
  String db=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(db!=null,"native isolated integration qualification required");if(!db.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_INTEGRATION_DB_REQUIRED");
  workload=new ECKeyGenerator(Curve.P_256).keyID("synthetic-workload").keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).generate();staff=new ECKeyGenerator(Curve.P_256).keyID("synthetic-staff").generate();var identity=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);issuer="http://127.0.0.1:"+identity.getAddress().getPort()+"/realms/synthetic-jobs";
  identity.createContext("/realms/synthetic-jobs/protocol/openid-connect/certs",exchange->{byte[] bytes=new JWKSet(workload.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}});
  identity.createContext("/realms/synthetic-jobs/protocol/openid-connect/token",exchange->{try{String form=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);if(!form.contains("client_id=nigson-integration")||!form.contains("grant_type=client_credentials"))throw new IllegalArgumentException("WORKLOAD_FORM_INVALID");byte[] bytes=EventCodec.JSON.writeValueAsBytes(Map.of("token_type","Bearer","access_token",token("nigson-integration","payment.reconcile","nigson-commerce")));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}}catch(Exception invalid){exchange.sendResponseHeaders(401,-1);exchange.close();}});
  identity.createContext("/internal/v1/payments/reconciliation",exchange->{try{var jwt=SignedJWT.parse(exchange.getRequestHeaders().getFirst("Authorization").substring(7));if(!jwt.verify(new ECDSAVerifier(workload.toECPublicKey()))||!jwt.getJWTClaimsSet().getAudience().contains("nigson-commerce")||!jwt.getJWTClaimsSet().getStringClaim("azp").equals("nigson-integration")||!jwt.getJWTClaimsSet().getStringClaim("scope").equals("payment.reconcile"))throw new IllegalArgumentException("WORKLOAD_WIRE_INVALID");var body=EventCodec.JSON.readTree(exchange.getRequestBody().readAllBytes());if(!body.path("jobId").isString()||body.has("cursor"))throw new IllegalArgumentException("PRIVATE_RECONCILIATION_INPUT_INVALID");byte[] bytes="{\"processed\":0,\"mismatches\":0,\"nextCursor\":null}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(202,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}}catch(Exception invalid){exchange.sendResponseHeaders(401,-1);exchange.close();}});
  identity.start();
  try(var app=new SpringApplicationBuilder(IntegrationApplication.class).run("--server.port=0","--server.address=127.0.0.1","--APP_ENV=test","--NIGSON_SECURITY_ENABLED=true","--NIGSON_EVENTS_ENABLED=false","--NIGSON_PROVIDER_JOBS_ENABLED=true","--NIGSON_PROVIDER_WORKER_ENABLED=false","--NIGSON_INTEGRATION_WORKLOAD_SECRET=mock-integration-workload-configuration-only","--NIGSON_COMMERCE_BASE_URL=http://127.0.0.1:"+identity.getAddress().getPort(),"--NIGSON_OIDC_ISSUER="+issuer,"--NIGSON_PRIVATE_PEER_CIDRS=127.0.0.1/32","--NIGSON_ACTOR_PUBLIC_KEYS="+EventCodec.JSON.writeValueAsString(Map.of(staff.getKeyID(),Base64.getEncoder().encodeToString(staff.toECPublicKey().getEncoded()))),"--spring.datasource.url="+db+"?currentSchema=integration","--spring.datasource.username=nigson_integration_app","--spring.datasource.password=mock-integration-runtime")){
   base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();var jdbc=app.getBean(JdbcTemplate.class);job=UUID.randomUUID();jdbc.update("INSERT INTO integration.integration_job(id,created_by,updated_by,kind,business_key,owner_service,resource_id,state,attempt_count,next_attempt_at,error_code) VALUES (?,'synthetic','synthetic','PAYMENT_RECONCILE',?,'commerce',?,'FAILED',2,now(),'PROVIDER_UNAVAILABLE')",job,"http-private:"+job,UUID.randomUUID());
   try{
    String read=token("nigson-commerce","jobs.read"),write=token("nigson-commerce","jobs.retry_WITH_SIGNED_STAFF");var page=get(read,"?kind=PAYMENT_RECONCILE&state=FAILED&page=0&size=1&sort=id,asc");assertThat(page.statusCode()).isEqualTo(200);assertThat(page.headers().firstValue("Cache-Control")).contains("no-store");var dto=EventCodec.JSON.readTree(page.body()).path("items").get(0);assertThat(dto.propertyNames()).containsExactlyInAnyOrder("id","state","attemptCount","nextAttemptAt","lastErrorCode");assertThat(page.body()).doesNotContain("businessKey").doesNotContain("resourceId");
    assertThat(get(token("nigson-experience","jobs.read"),"").statusCode()).isEqualTo(401);assertThat(get(write,"").statusCode()).isEqualTo(401);assertThat(get(read,"?state=FAILED&state=UNKNOWN").statusCode()).isEqualTo(400);assertThat(get(read,"?providerKey=secret").statusCode()).isEqualTo(400);
    String path="/internal/v1/jobs/"+job+"/retry",body="{\"reason\":\"Inspect original durable operation\"}";UUID correlation=UUID.randomUUID();String key=UUID.randomUUID().toString(),signed=actor(path,body,correlation,Set.of("OPERATIONS"),Instant.now());
    assertThat(retry(write,null,body,correlation,key,"\"0\"").statusCode()).isEqualTo(401);assertThat(retry(write,actor(path,body,correlation,Set.of("ADMIN"),Instant.now()),body,correlation,key,"\"0\"").statusCode()).isEqualTo(401);assertThat(retry(write,actor(path,body,correlation,Set.of("OPERATIONS"),Instant.now().minusSeconds(901)),body,correlation,key,"\"0\"").statusCode()).isEqualTo(401);
    assertThat(retry(write,signed,"{\"reason\":\"Changed signed body\"}",correlation,key,"\"0\"").statusCode()).isEqualTo(401);assertThat(retry(write,signed,body,correlation,UUID.randomUUID().toString(),"\"9\"").statusCode()).isEqualTo(412);
    var accepted=retry(write,signed,body,correlation,key,"\"0\"");assertThat(accepted.statusCode()).isEqualTo(202);assertThat(accepted.headers().firstValue("ETag")).contains("\"1\"");assertThat(EventCodec.JSON.readTree(accepted.body()).path("state").asString()).isEqualTo("PENDING");assertThat(retry(write,signed,body,correlation,key,"\"0\"").body()).isEqualTo(accepted.body());assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,job)).isEqualTo(1);
    jdbc.update("UPDATE integration.integration_job SET state='UNKNOWN',version=2 WHERE id=?",job);String unknownKey=UUID.randomUUID().toString();assertThat(retry(write,signed,body,correlation,unknownKey,"\"2\"").statusCode()).isEqualTo(503);assertThat(jdbc.queryForObject("SELECT state FROM integration.integration_job WHERE id=?",String.class,job)).isEqualTo("UNKNOWN");
    assertThat(app.getBeansOfType(com.nigson.integration.platform.EventHandler.class).values().stream().map(com.nigson.integration.platform.EventHandler::eventType).toList()).contains("order.pending.v1");assertThat(app.getBean(JobWorker.class).poll()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT state FROM integration.integration_job WHERE id=?",String.class,job)).isEqualTo("SUCCEEDED");assertThat(app.getBean(io.micrometer.core.instrument.MeterRegistry.class).get("nigson.integration.jobs").tags("owner","integration","kind","PAYMENT_RECONCILE","state","SUCCEEDED").counter().count()).isEqualTo(1);
   }finally{jdbc.update("DELETE FROM integration.integration_job WHERE id=?",job);}
  }finally{identity.stop(0);}
 }
}
