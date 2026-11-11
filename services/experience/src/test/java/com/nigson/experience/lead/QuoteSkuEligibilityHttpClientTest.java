package com.nigson.experience.lead;

import com.nigson.shared.events.EventCodec;import com.nigson.shared.problem.Problem;import com.nigson.shared.security.PrivateWorkload;import com.sun.net.httpserver.HttpServer;import java.net.*;import java.net.http.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class QuoteSkuEligibilityHttpClientTest {
 @Test void commerceSkuEligibilityUsesExactScopedHttpAndRejectsAbsentOrMalformedEligibility()throws Exception{UUID party=UUID.randomUUID(),market=UUID.randomUUID(),sku=UUID.randomUUID();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();server.createContext("/internal/v1/quote-sku-eligibility",x->{calls.incrementAndGet();assertThat(x.getRequestMethod()).isEqualTo("POST");assertThat(x.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-quote-token");var body=EventCodec.JSON.readTree(x.getRequestBody().readAllBytes());assertThat(body.path("partyId").asString()).isEqualTo(party.toString());assertThat(body.path("marketId").asString()).isEqualTo(market.toString());UUID requested=UUID.fromString(body.path("items").get(0).path("skuId").asString());int status=requested.equals(sku)?200:422;byte[] response=EventCodec.JSON.writeValueAsBytes(status==200?Map.of("items",List.of(Map.of("skuId",sku,"quantityScale",2))):Map.of("type","about:blank","title","Quote rule violation","status",422));x.getResponseHeaders().set("Content-Type",status==200?"application/json":"application/problem+json");x.sendResponseHeaders(status,response.length);x.getResponseBody().write(response);x.close();});server.start();try{var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-206")).findFirst().orElseThrow();var client=new HttpQuoteSkuEligibilityClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),r->{assertThat(r).isEqualTo(route);return "local-quote-token";},HttpClient.newHttpClient());assertThat(client.validate(party,market,null,List.of(new QuoteLeadValues.Product(sku,new java.math.BigDecimal("1.25"))))).containsExactly(new QuoteLeadValues.EligibleSku(sku,2));assertThatThrownBy(()->client.validate(party,market,null,List.of(new QuoteLeadValues.Product(UUID.randomUUID(),java.math.BigDecimal.ONE)))).isInstanceOf(Problem.Failure.class).hasMessage("QUOTE_RULE_VIOLATION");assertThat(calls).hasValue(2);}finally{server.stop(0);}}

 @Test void resolvesServiceTokenThroughConfiguredBackchannelUsingExperienceIdentity()throws Exception {
  UUID party=UUID.randomUUID(),market=UUID.randomUUID(),sku=UUID.randomUUID();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
  server.createContext("/realms/quote/protocol/openid-connect/token",x->{
   calls.incrementAndGet();assertThat(x.getRequestHeaders().getFirst("Authorization")).isEqualTo("Basic "+Base64.getEncoder().encodeToString("nigson-experience:synthetic-experience-secret-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   assertThat(new String(x.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).contains("scope=catalogue.quote-eligibility.read");
   byte[] body=EventCodec.JSON.writeValueAsBytes(Map.of("access_token","synthetic-private-token"));x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();
  });
  server.createContext("/internal/v1/quote-sku-eligibility",x->{
   assertThat(x.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-private-token");
   byte[] body=EventCodec.JSON.writeValueAsBytes(Map.of("items",List.of(Map.of("skuId",sku,"quantityScale",0))));x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();
  });
  server.start();
  try {
   String base="http://127.0.0.1:"+server.getAddress().getPort();
   var env=new org.springframework.mock.env.MockEnvironment().withProperty("APP_ENV","test").withProperty("NIGSON_COMMERCE_PRIVATE_URL",base).withProperty("NIGSON_OIDC_ISSUER","http://127.0.0.1:1/realms/quote").withProperty("NIGSON_OIDC_JWKS",base+"/realms/quote/protocol/openid-connect/certs").withProperty("NIGSON_QUOTE_CATALOGUE_WORKLOAD_CLIENT_ID","nigson-experience").withProperty("NIGSON_QUOTE_CATALOGUE_WORKLOAD_CLIENT_SECRET","synthetic-experience-secret-32-bytes");
   assertThat(HttpQuoteSkuEligibilityClient.from(env).validate(party,market,null,List.of(new QuoteLeadValues.Product(sku,java.math.BigDecimal.ONE)))).containsExactly(new QuoteLeadValues.EligibleSku(sku,0));assertThat(calls).hasValue(1);
   env.withProperty("NIGSON_QUOTE_CATALOGUE_WORKLOAD_CLIENT_ID","nigson-commerce");assertThatThrownBy(()->HttpQuoteSkuEligibilityClient.from(env)).isInstanceOf(IllegalArgumentException.class);
   env.withProperty("NIGSON_QUOTE_CATALOGUE_WORKLOAD_CLIENT_ID","nigson-experience").withProperty("NIGSON_OIDC_JWKS",base+"/realms/other/protocol/openid-connect/certs");assertThatThrownBy(()->HttpQuoteSkuEligibilityClient.from(env)).isInstanceOf(IllegalArgumentException.class);
  } finally {server.stop(0);}
 }
}
