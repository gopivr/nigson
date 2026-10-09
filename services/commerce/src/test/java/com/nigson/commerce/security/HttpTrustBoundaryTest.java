package com.nigson.commerce.security;
import com.nigson.shared.security.*;
import com.nigson.commerce.platform.ProblemWriter;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.security.interfaces.ECPublicKey;
import static org.assertj.core.api.Assertions.*;
class HttpTrustBoundaryTest {
 @Test void actualHttpCookieCsrfCurrentOwnerWorkloadActorBodyAndSafeErrorBoundaries()throws Exception {
  String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"make security-check supplies isolated owner PostgreSQL");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");var source=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));var clock=Clock.systemUTC();UUID party=UUID.randomUUID();jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic fixture')",party,SecurityProtocolTest.ISSUER,party.toString(),party+"@synthetic.invalid");var codec=new SessionCodec("v1",Map.of("v1",new byte[32]));var sessions=new JdbcSessionTrust(jdbc,tx,codec,clock,Set.of("/account"));var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());var record=new SessionRecord(1,party,SecurityProtocolTest.ISSUER,party.toString(),clock.instant(),null,"synthetic-access","synthetic-refresh",clock.instant().plusSeconds(300),JdbcSessionTrust.opaque(),SessionRecord.TokenState.READY);String id=sessions.authenticate(flow.sessionId(),record);var cookie=new SessionCookies("local",true);var keys=SecurityProtocolTest.pair();
  var trust=new PrivateWorkload("commerce",5,new PrivateIngress(List.of("127.0.0.1/32")),r->token->{if(!token.equals("local-workload"))throw ActorAssertion.denied();return Jwt.withTokenValue(token).header("alg","RS256").subject("service").claim("azp",r.client()).claim("scope",r.scope()).build();},Map.of("active",(ECPublicKey)keys.getPublic()),Set.of(SecurityProtocolTest.ISSUER),clock);
  var filter=new TrustFilter(trust,sessions,cookie,new CommerceAuthority(jdbc,clock,5),new ProblemWriter("test",""),new BrowserBoundary("test",Set.of("https://bff.synthetic.test")));
  var server=new TomcatServletWebServerFactory(0).getWebServer(context->{
   context.addFilter("security-fixture",(request,response,chain)->{try{filter.doFilter(request,response,chain);}finally{SecurityContextHolder.clearContext();}}).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),false,"/*");
   context.addServlet("owned-fixture",new HttpServlet(){protected void service(HttpServletRequest r,HttpServletResponse s)throws java.io.IOException {var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null){s.setStatus(401);return;}Object principal=auth.getPrincipal();if(r.getRequestURI().startsWith("/internal/")){if(!(principal instanceof PrivateWorkload.Proof))s.setStatus(403);else{s.getOutputStream().write(r.getInputStream().readAllBytes());s.setStatus(200);}}else {if(!(principal instanceof ActorAssertion.Actor a)||!a.party().equals(party))s.setStatus(404);else s.setStatus(204);}}}).addMapping("/*");
  });
  try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()){server.start();String base="http://127.0.0.1:"+server.getPort();
   var get=HttpRequest.newBuilder(URI.create(base+"/api/v1/owned-fixture")).header("Cookie","nigson-local-session="+id).GET().build();assertThat(client.send(get,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
   for(var bad:List.of(HttpRequest.newBuilder(URI.create(base+"/api/v1/owned-fixture")).header("Cookie","nigson-local-session="+id).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpRequest.newBuilder(URI.create(base+"/api/v1/owned-fixture?access_token=private-query-material")).header("Cookie","nigson-local-session="+id).GET().build(),HttpRequest.newBuilder(URI.create(base+"/api/v1/owned-fixture")).header("Cookie","nigson-local-session="+id).header("Origin","https://attacker.synthetic.test").GET().build())){var response=client.send(bad,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(401);assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");assertThat(response.body()).contains("AUTH_REQUIRED").doesNotContain("private-query-material",party.toString(),"synthetic-refresh");}
   assertThat(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/owned-fixture")).header("Cookie","nigson-local-session="+id).header("X-CSRF-TOKEN",record.csrfToken()).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
   var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-124")).findFirst().orElseThrow();UUID correlation=UUID.randomUUID();byte[] bytes="{\"orderId\":\"11111111-1111-1111-1111-111111111111\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);var binding=new ActorAssertion.Binding("commerce",route.scope(),route.method(),route.path(),correlation,ActorAssertion.Binding.bodyHash(bytes));String actor=SecurityProtocolTest.sign(keys,ActorAssertion.claims(new ActorAssertion.Actor(party,SecurityProtocolTest.ISSUER,party.toString(),null,Set.of("CUSTOMER"),Set.of(route.scope()),null),binding,clock.instant()));
   var privateRequest=HttpRequest.newBuilder(URI.create(base+route.path())).header("Authorization","Bearer local-workload").header("X-Nigson-Actor",actor).header("X-Correlation-ID",correlation.toString()).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();var accepted=client.send(privateRequest,HttpResponse.BodyHandlers.ofString());assertThat(accepted.statusCode()).isEqualTo(200);assertThat(accepted.body()).isEqualTo(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
   var altered=HttpRequest.newBuilder(URI.create(base+route.path())).header("Authorization","Bearer local-workload").header("X-Nigson-Actor",actor).header("X-Correlation-ID",correlation.toString()).POST(HttpRequest.BodyPublishers.ofString("{}" )).build();assertThat(client.send(altered,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
   var cookiePrivate=HttpRequest.newBuilder(URI.create(base+route.path())).header("Cookie","nigson-local-session="+id).POST(HttpRequest.BodyPublishers.ofString("{}")).build();assertThat(client.send(cookiePrivate,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
   sessions.revokeSubject(party);assertThat(client.send(get,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
  }finally{server.stop();server.destroy();}
 }
}
