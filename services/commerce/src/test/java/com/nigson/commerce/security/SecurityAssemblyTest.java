package com.nigson.commerce.security;
import com.nigson.commerce.CommerceApplication;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.util.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
class SecurityAssemblyTest {
 @Test void enabledSecurityBootsWithTypedJdbcAndOneDenyByDefaultChain()throws Exception {
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"make security-check supplies isolated owner PostgreSQL");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");var keys=SecurityProtocolTest.pair();String publicKey=Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());byte[] raw=new byte[32];new java.security.SecureRandom().nextBytes(raw);
  var properties=new HashMap<String,Object>();properties.put("APP_ENV","test");properties.put("NIGSON_SECURITY_ENABLED","true");properties.put("NIGSON_PROVIDERS_ENABLED","false");properties.put("NIGSON_EVENTS_ENABLED","false");properties.put("NIGSON_JDBC_URL",url+"?currentSchema=commerce");properties.put("NIGSON_DB_USER","nigson_commerce_app");properties.put("NIGSON_DB_PASSWORD","mock-commerce-runtime");properties.put("NIGSON_AWS_REGION","eu-west-1");properties.put("NIGSON_KMS_LOCAL_ENDPOINT","http://127.0.0.1:9");properties.put("NIGSON_OIDC_ISSUER","http://127.0.0.1:9/realms/offline");properties.put("NIGSON_OIDC_JWKS","http://127.0.0.1:9/realms/offline/protocol/openid-connect/certs");properties.put("NIGSON_BFF_CLIENT_SECRET","synthetic-bff-credential-with-at-least-32-bytes");properties.put("NIGSON_COMMERCE_WORKLOAD_SECRET","synthetic-workload-credential-with-at-least-32-bytes");properties.put("NIGSON_BFF_CALLBACK","https://bff.synthetic.test/bff/callback");properties.put("NIGSON_BFF_RETURN_PATHS","/account");properties.put("NIGSON_BFF_ALLOWED_ORIGINS","https://bff.synthetic.test");properties.put("NIGSON_SESSION_KEY_REVISION","v1");properties.put("NIGSON_SESSION_LOCAL_KEY",Base64.getEncoder().encodeToString(raw));properties.put("NIGSON_PRIVATE_PEER_CIDRS","127.0.0.1/32");properties.put("NIGSON_ACTOR_KMS_KEY",KmsIdentityTest.ARN);properties.put("NIGSON_ACTOR_KEY_ID","active");properties.put("NIGSON_ACTOR_PUBLIC_KEY",publicKey);properties.put("NIGSON_ACTOR_PUBLIC_KEYS","{\"active\":\""+publicKey+"\"}");properties.put("NIGSON_PUBLIC_ASSET_BASE_URL","https://cdn.synthetic.test/assets/");properties.put("server.port","0");properties.put("logging.level.root","WARN");
  var workload=System.getenv("NIGSON_TEST_STORAGE_FIXTURE")==null?null:new com.nigson.acceptance.files.FileHttpAcceptance.Workload();
  com.nigson.acceptance.files.FileHttpAcceptance.configure(properties);
  if(workload!=null){properties.put("NIGSON_OIDC_ISSUER",workload.issuer);properties.put("NIGSON_OIDC_JWKS",workload.issuer+"/protocol/openid-connect/certs");}
  var jobs=System.getenv("NIGSON_TEST_JOBS")==null?null:new com.nigson.acceptance.provider.JobHttpAcceptance.Fixture(keys);
  if(jobs!=null)jobs.configure(properties);
  if(System.getenv("NIGSON_TEST_NOTIFICATION")!=null)properties.put("NIGSON_NOTIFICATION_TEMPLATES_ENABLED","true");
  try(workload;jobs;var app=new SpringApplicationBuilder(CommerceApplication.class).properties(properties).run();var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
   assertThat(app.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).containsOnlyKeys("trustedSecurity");assertThat(app.getBean("springSessionConversionService",org.springframework.core.convert.ConversionService.class)).isNotNull();assertThat(app.getBean(SessionCookies.class)).isNotNull();String base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
   com.nigson.acceptance.AuthorizationHttpAcceptance.run(app,http,base);
   com.nigson.acceptance.party.PartyHttpAcceptance.run(app,http,base);
   com.nigson.acceptance.configuration.ConfigurationHttpAcceptance.run(app,http,base);
   com.nigson.acceptance.catalogue.CatalogueHttpAcceptance.run(app,http,base);
   com.nigson.acceptance.files.FileHttpAcceptance.run(app,http,base,workload);
   com.nigson.acceptance.provider.JobHttpAcceptance.run(app,http,base,jobs);
   com.nigson.acceptance.notification.NotificationHttpAcceptance.run(app,http,base,jobs);
   com.nigson.acceptance.inventory.InventoryHttpAcceptance.run(app,http,base);
   com.nigson.acceptance.market.MarketHttpAcceptance.run(app,http,base);
   assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/actuator/health/readiness")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
   for(String path:List.of("/api/v1/staff/roles","/bff/logout","/internal/v1/payments/reconciliation")){var response=http.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isIn(401,403);assertThat(response.body()).doesNotContain("synthetic-bff-credential",publicKey,"mock-commerce-runtime");}
   var origin=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/owned-fixture")).method("OPTIONS",HttpRequest.BodyPublishers.noBody()).header("Origin","https://bff.synthetic.test").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-csrf-token").build(),HttpResponse.BodyHandlers.ofString());assertThat(origin.statusCode()).isEqualTo(204);assertThat(origin.headers().firstValue("Access-Control-Allow-Origin")).hasValue("https://bff.synthetic.test");
  }
 }
}
