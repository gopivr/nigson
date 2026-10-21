package com.nigson.acceptance.notification;

import com.nigson.integration.IntegrationApplication;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

class NotificationTemplateHttpTest {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private ECKey workload,staff;private HttpServer identity;private String issuer,base;private final UUID party=UUID.randomUUID();private final String scope="templates.write_WITH_SIGNED_ADMIN";
 private String token(String client,String granted)throws Exception{Instant now=Instant.now();var value=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(workload.getKeyID()).type(JOSEObjectType.JWT).build(),new JWTClaimsSet.Builder().issuer(issuer).subject("synthetic-template-workload").audience("nigson-integration").claim("azp",client).claim("client_id",client).claim("typ","Bearer").claim("scope",granted).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(120))).build());value.sign(new ECDSASigner(workload));return value.serialize();}
 private String actor(String body,UUID correlation,Set<String> roles,UUID organization,Instant mfa)throws Exception{var value=new ActorAssertion.Actor(party,issuer,"synthetic-template-admin",organization,roles,Set.of(scope),mfa);var binding=new ActorAssertion.Binding("integration",scope,"POST","/internal/v1/templates",correlation,ActorAssertion.Binding.bodyHash(body.getBytes(StandardCharsets.UTF_8)));var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(staff.getKeyID()).type(new JOSEObjectType("nigson-actor+jwt")).build(),ActorAssertion.claims(value,binding,Instant.now()));jwt.sign(new ECDSASigner(staff));return jwt.serialize();}
 private HttpResponse<String> create(String workloadToken,String signed,String body,UUID correlation,String key,String match)throws Exception{var request=HttpRequest.newBuilder(URI.create(base+"/internal/v1/templates")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+workloadToken);if(signed!=null)request.header("X-Nigson-Actor",signed);request.header("X-Correlation-ID",correlation.toString()).header("Content-Type","application/json").header("Idempotency-Key",key);if(match!=null)request.header("If-Match",match);return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
 private String body(String code,int revision){return "{\"code\":\""+code+"\",\"revision\":"+revision+",\"locale\":\"en-NG\",\"channel\":\"EMAIL\",\"classification\":\"TRANSACTIONAL\",\"subject\":\"Greeting\",\"body\":\"<p>Hello {{name}}</p>\",\"variablesSchema\":{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\",\"maxLength\":80}},\"required\":[\"name\"]},\"active\":false,\"reason\":\"Synthetic offline template fixture\"}";}

 @Test void signedHttpCreatesImmutableTemplateAndEnforcesAuthorizationIdempotencyAndVersion()throws Exception{
  String db=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(db!=null,"native integration-owned PostgreSQL required");if(!db.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_INTEGRATION_DB_REQUIRED");
  workload=new ECKeyGenerator(Curve.P_256).keyID("synthetic-template-workload").keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).generate();staff=new ECKeyGenerator(Curve.P_256).keyID("synthetic-template-admin").generate();identity=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);issuer="http://127.0.0.1:"+identity.getAddress().getPort()+"/realms/synthetic-templates";
  identity.createContext("/realms/synthetic-templates/protocol/openid-connect/certs",x->{byte[] bytes=new JWKSet(workload.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}});identity.start();
  var properties=new HashMap<String,Object>();properties.put("APP_ENV","test");properties.put("NIGSON_SECURITY_ENABLED","true");properties.put("NIGSON_PRODUCT_PHASE","1");properties.put("NIGSON_EVENTS_ENABLED","false");properties.put("NIGSON_PROVIDER_JOBS_ENABLED","false");properties.put("NIGSON_JDBC_URL",db+"?currentSchema=integration");properties.put("NIGSON_DB_USER","nigson_integration_app");properties.put("NIGSON_DB_PASSWORD","mock-integration-runtime");properties.put("NIGSON_OIDC_ISSUER",issuer);properties.put("NIGSON_PRIVATE_PEER_CIDRS","127.0.0.1/32");properties.put("NIGSON_ACTOR_PUBLIC_KEYS",EventCodec.JSON.writeValueAsString(Map.of(staff.getKeyID(),Base64.getEncoder().encodeToString(staff.toECPublicKey().getEncoded()))));properties.put("server.port","0");properties.put("server.address","127.0.0.1");properties.put("logging.level.root","WARN");
  try(var app=new SpringApplicationBuilder(IntegrationApplication.class).properties(properties).run()){
   base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();JdbcTemplate jdbc=app.getBean(JdbcTemplate.class);String code="TEST_"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);String input=body(code,1);UUID corr=UUID.randomUUID();String workloadToken=token("nigson-commerce",scope),wrongClient=token("nigson-experience",scope),wrongScope=token("nigson-commerce","jobs.read");
   assertThat(create(workloadToken,null,input,corr,UUID.randomUUID().toString(),"\"0\"").statusCode()).isEqualTo(401);
   assertThat(create(wrongClient,actor(input,corr,Set.of("ADMIN"),null,Instant.now()),input,corr,UUID.randomUUID().toString(),"\"0\"").statusCode()).isEqualTo(401);
   assertThat(create(wrongScope,actor(input,corr,Set.of("ADMIN"),null,Instant.now()),input,corr,UUID.randomUUID().toString(),"\"0\"").statusCode()).isEqualTo(401);
   assertThat(create(workloadToken,actor(input,corr,Set.of("CONTENT_EDITOR"),null,Instant.now()),input,corr,UUID.randomUUID().toString(),"\"0\"").statusCode()).isIn(401,403);
   assertThat(create(workloadToken,actor(input,corr,Set.of("ADMIN"),null,Instant.now().minusSeconds(901)),input,corr,UUID.randomUUID().toString(),"\"0\"").statusCode()).isEqualTo(401);
   assertThat(create(workloadToken,actor(input,corr,Set.of("ADMIN"),UUID.randomUUID(),Instant.now()),input,corr,UUID.randomUUID().toString(),"\"0\"").statusCode()).isEqualTo(403);
   String unsafe=input.replace("{{name}}","{{unknown}}");var denied=create(workloadToken,actor(unsafe,corr,Set.of("ADMIN"),null,Instant.now()),unsafe,corr,UUID.randomUUID().toString(),"\"0\"");assertThat(denied.statusCode()).isEqualTo(422);assertThat(denied.body()).doesNotContain("unknown");assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.notification_template WHERE code=?",Integer.class,code)).isZero();
   String key=UUID.randomUUID().toString();var started=System.nanoTime();var first=create(workloadToken,actor(input,corr,Set.of("ADMIN"),null,Instant.now()),input,corr,key,null);assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(201);assertThat(first.headers().firstValue("ETag")).hasValue("\"1\"");assertThat(first.headers().firstValue("Cache-Control")).hasValue("no-store");var response=EventCodec.JSON.readTree(first.body());assertThat(response.propertyNames()).containsExactlyInAnyOrder("id","code","revision","active");UUID id=UUID.fromString(response.path("id").asString());
   var replay=create(workloadToken,actor(input,corr,Set.of("ADMIN"),null,Instant.now()),input,corr,key,null);assertThat(replay.statusCode()).isEqualTo(201);assertThat(EventCodec.JSON.readTree(replay.body())).isEqualTo(EventCodec.JSON.readTree(first.body()));assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.notification_template WHERE code=?",Integer.class,code)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id=?",Integer.class,id)).isEqualTo(1);
   var stale=create(workloadToken,actor(input,corr,Set.of("ADMIN"),null,Instant.now()),input,corr,UUID.randomUUID().toString(),"\"0\"");assertThat(stale.statusCode()).isEqualTo(412);String changed=input.replace("Greeting","Changed greeting");var conflict=create(workloadToken,actor(changed,corr,Set.of("ADMIN"),null,Instant.now()),changed,corr,key,null);assertThat(conflict.statusCode()).isEqualTo(409);
   String v2=body(code,2);var next=create(workloadToken,actor(v2,corr,Set.of("ADMIN"),null,Instant.now()),v2,corr,UUID.randomUUID().toString(),"\"1\"");assertThat(next.statusCode()).isEqualTo(201);assertThat(next.headers().firstValue("ETag")).hasValue("\"2\"");assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.notification_template WHERE code=?",Integer.class,code)).isEqualTo(2);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.audit_record WHERE resource_id IN (SELECT id FROM integration.notification_template WHERE code=?) AND new_value::text LIKE '%Hello {{name}}%'",Integer.class,code)).isZero();
   String v3=body(code,3),raceKey=UUID.randomUUID().toString();try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {var results=new ArrayList<Future<HttpResponse<String>>>();for(int i=0;i<8;i++)results.add(workers.submit(()->{for(int attempt=0;attempt<50;attempt++){var responseRace=create(workloadToken,actor(v3,corr,Set.of("ADMIN"),null,Instant.now()),v3,corr,raceKey,"\"2\"");if(responseRace.statusCode()==201)return responseRace;if(responseRace.statusCode()!=409)throw new AssertionError("Unexpected template idempotency race status "+responseRace.statusCode()+": "+responseRace.body());Thread.sleep(10);}throw new AssertionError("Template idempotency race did not settle");}));JsonNode expected=null;for(var result:results){var responseRace=result.get(15,TimeUnit.SECONDS);if(expected==null)expected=EventCodec.JSON.readTree(responseRace.body());else assertThat(EventCodec.JSON.readTree(responseRace.body())).isEqualTo(expected);}}
   assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.notification_template WHERE code=?",Integer.class,code)).isEqualTo(3);assertThat(System.nanoTime()-started).isLessThan(Duration.ofSeconds(30).toNanos());
  }finally{identity.stop(0);}
 }
}
