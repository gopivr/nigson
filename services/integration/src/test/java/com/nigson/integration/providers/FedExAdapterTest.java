package com.nigson.integration.providers;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ProviderHttp;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

/** Actual loopback HTTP with token state; no genuine provider credentials or calls. */
class FedExAdapterTest {
 @Test void trackingBatchIsBoundedBeforeAnyHttpAndUsesExactPhysicalNumbers()throws Exception{var calls=new AtomicInteger();var seen=new java.util.concurrent.atomic.AtomicReference<tools.jackson.databind.JsonNode>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{var input=e.getRequestBody().readAllBytes();Object output;if(e.getRequestURI().getPath().equals("/oauth/token"))output=Map.of("access_token","mock-token","token_type","bearer","expires_in",3600);else{calls.incrementAndGet();seen.set(EventCodec.JSON.readTree(input));output=Map.of("output",Map.of("completeTrackResults",List.of()));}byte[] bytes=EventCodec.JSON.writeValueAsBytes(output);e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();try{var carrier=adapter(server);assertThatThrownBy(()->carrier.track(List.of())).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->carrier.track(List.of("123456789012","123456789012"))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->carrier.track(java.util.stream.IntStream.range(0,31).mapToObj(n->Long.toString(123456789012L+n)).toList())).isInstanceOf(IllegalArgumentException.class);assertThat(calls.get()).isZero();carrier.track(List.of("123456789012","123456789013"));assertThat(calls.get()).isEqualTo(1);assertThat(seen.get().path("trackingInfo").size()).isEqualTo(2);assertThat(seen.get().path("trackingInfo").path(1).path("trackingNumberInfo").path("trackingNumber").asString()).isEqualTo("123456789013");}finally{server.stop(0);}}
 @Test void shippingDocumentResponseMayExceedOrdinaryProviderLimitWithoutIncreasingRateLimit()throws Exception{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String padding="a".repeat(1_100_000);server.createContext("/",e->{e.getRequestBody().readAllBytes();String path=e.getRequestURI().getPath();Object result=path.equals("/oauth/token")?Map.of("access_token","mock-access","token_type","bearer","expires_in",3600):path.equals("/ship/v1/shipments")?Map.of("output",Map.of("transactionShipments",List.of(Map.of("masterTrackingNumber","123456789012","encodedLabel",padding)))):Map.of("output",Map.of("rateReplyDetails",List.of(),"padding",padding));byte[] bytes=EventCodec.JSON.writeValueAsBytes(result);e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();try{var adapter=adapter(server);var input=EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of()));assertThat(adapter.book(input).path("transactionShipments").path(0).path("encodedLabel").asString()).hasSize(padding.length());assertThatThrownBy(()->adapter.rates(input)).isInstanceOf(ProviderHttp.Failure.class);}finally{server.stop(0);}}
 @Test void readRenewsOnceAndUsesConfiguredAccountAndReference()throws Exception{
  var grants=new AtomicInteger();var reads=new AtomicInteger();var seen=new ArrayList<tools.jackson.databind.JsonNode>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{String path=x.getRequestURI().getPath();String body=new String(x.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);int status=200;Object result;
   if(path.equals("/oauth/token")){grants.incrementAndGet();result=Map.of("access_token","mock-access-"+grants.get(),"token_type","bearer","expires_in",3600);}
   else{seen.add(EventCodec.JSON.readTree(body));reads.incrementAndGet();if(reads.get()==1){status=401;result=Map.of("errors",List.of(Map.of("code","EXPIRED")));}else{assertThat(x.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer mock-access-2");result=Map.of("output",Map.of("completeTrackResults",List.of()));}}
   byte[] bytes=EventCodec.JSON.writeValueAsBytes(result);x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();
  });server.start();
  try{var adapter=adapter(server);var result=adapter.trackReference("booking-synthetic-1",LocalDate.of(2026,10,10),LocalDate.of(2026,10,12));assertThat(result.path("completeTrackResults").isEmpty()).isTrue();assertThat(grants.get()).isEqualTo(2);assertThat(reads.get()).isEqualTo(2);assertThat(seen.getFirst().path("referencesInformation").path("accountNumber").asString()).isEqualTo("mock-fedex-account");assertThat(seen.get(1)).isEqualTo(seen.getFirst());assertThat(seen.getFirst().path("referencesInformation").path("shipDateEnd").asString()).isEqualTo("2026-10-12");}
  finally{server.stop(0);}
 }
 @Test void unauthorizedWriteIsNeverAutomaticallyRepeated()throws Exception{
  var grants=new AtomicInteger();var writes=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{x.getRequestBody().readAllBytes();boolean token=x.getRequestURI().getPath().equals("/oauth/token");if(token)grants.incrementAndGet();else writes.incrementAndGet();byte[] b=EventCodec.JSON.writeValueAsBytes(token?Map.of("access_token","mock-access","token_type","bearer","expires_in",3600):Map.of("errors",List.of(Map.of("code","EXPIRED"))));x.sendResponseHeaders(token?200:401,b.length);x.getResponseBody().write(b);x.close();});server.start();
  try{var adapter=adapter(server);assertThatThrownBy(()->adapter.book(EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of())))).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->{assertThat(f.status).isEqualTo(401);assertThat(f.uncertain).isFalse();});assertThat(grants.get()).isEqualTo(1);assertThat(writes.get()).isEqualTo(1);}
  finally{server.stop(0);}
 }
 @Test void invalidTokenIsSafeAndDoesNotReachShipmentEndpoint()throws Exception{
  var writes=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{x.getRequestBody().readAllBytes();if(!x.getRequestURI().getPath().equals("/oauth/token"))writes.incrementAndGet();byte[] b=EventCodec.JSON.writeValueAsBytes(Map.of("access_token","mock-private\r\nvalue","token_type","bearer","expires_in","3600"));x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
  try{assertThatThrownBy(()->adapter(server).book(EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of())))).isInstanceOf(ProviderHttp.Failure.class).hasMessage("FEDEX_TOKEN_INVALID");assertThat(writes.get()).isZero();}finally{server.stop(0);}
 }
 @Test void invalidModeReferenceOrTransactionCannotSendHttp(){
  assertThatThrownBy(()->new FedExAdapter("prod",ProviderHttp.Mode.LIVE,"https://apis.fedex.com","synthetic-client","synthetic-secret","synthetic-account")).hasMessage("PROVIDER_CREDENTIAL_MODE_INVALID");
  var adapter=new FedExAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:9","mock-client","mock-secret","mock-account");
  assertThatThrownBy(()->adapter.trackReference("../private",LocalDate.now(),LocalDate.now())).hasMessage("FEDEX_REFERENCE_INVALID");
  assertThatThrownBy(()->adapter.trackReference("booking",LocalDate.now(),LocalDate.now().plusDays(91))).hasMessage("FEDEX_REFERENCE_INVALID");
  TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->adapter.rates(EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of())))).hasMessage("PROVIDER_CALL_INSIDE_TRANSACTION");}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
 }
 @Test void asynchronousAcceptancePollsExistingJobWithReadOnlyTokenRenewal()throws Exception{
  var grants=new AtomicInteger();var creates=new AtomicInteger();var polls=new AtomicInteger();var requests=new ArrayList<tools.jackson.databind.JsonNode>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{String path=x.getRequestURI().getPath();byte[] input=x.getRequestBody().readAllBytes();int status=200;Object response;
   if(path.equals("/oauth/token")){response=Map.of("access_token","mock-access-"+grants.incrementAndGet(),"token_type","bearer","expires_in",3600);}
   else if(path.equals("/ship/v1/shipments")){creates.incrementAndGet();response=Map.of("output",Map.of("jobId","synthetic-job-1"));}
   else if(path.equals("/ship/v1/shipments/results")){requests.add(EventCodec.JSON.readTree(input));if(polls.incrementAndGet()==1){status=401;response=Map.of("errors",List.of(Map.of("code","EXPIRED")));}else response=Map.of("output",Map.of("transactionShipments",List.of(Map.of("masterTrackingNumber","123456789012"))));}
   else{status=404;response=Map.of("errors",List.of());}
   byte[] bytes=EventCodec.JSON.writeValueAsBytes(response);x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();
  });server.start();
  try{var adapter=adapter(server);String job=adapter.book(EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of()))).path("jobId").asString();assertThat(job).isEqualTo("synthetic-job-1");assertThat(adapter.confirmed(job).path("transactionShipments").path(0).path("masterTrackingNumber").asString()).isEqualTo("123456789012");assertThat(creates.get()).isEqualTo(1);assertThat(polls.get()).isEqualTo(2);assertThat(grants.get()).isEqualTo(2);assertThat(requests.getFirst()).isEqualTo(EventCodec.JSON.valueToTree(Map.of("accountNumber",Map.of("value","mock-fedex-account"),"jobId",job)));assertThat(requests.getLast()).isEqualTo(requests.getFirst());}finally{server.stop(0);}
 }
 @Test void invalidAsyncAcceptanceRemainsUncertainAndUnavailableResultsNeverCreate()throws Exception{
  var creates=new AtomicInteger();var polls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{x.getRequestBody().readAllBytes();String path=x.getRequestURI().getPath();Object response;
   if(path.equals("/oauth/token"))response=Map.of("access_token","mock-token","token_type","bearer","expires_in",3600);
   else if(path.equals("/ship/v1/shipments")){creates.incrementAndGet();response=Map.of("output",Map.of("jobId","../unsafe"));}
   else{polls.incrementAndGet();response=Map.of("output",Map.of("transactionShipments",List.of()));}
   byte[] bytes=EventCodec.JSON.writeValueAsBytes(response);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
  });server.start();
  try{var adapter=adapter(server);assertThatThrownBy(()->adapter.book(EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of())))).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.uncertain).isTrue());assertThatThrownBy(()->adapter.confirmed("../unsafe")).hasMessage("FEDEX_JOB_INVALID");assertThat(polls.get()).isZero();assertThatThrownBy(()->adapter.confirmed("synthetic-job-1")).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->{assertThat(f.uncertain).isFalse();assertThat(f).hasMessage("FEDEX_RESULTS_UNAVAILABLE");});assertThat(creates.get()).isEqualTo(1);assertThat(polls.get()).isEqualTo(1);
   TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->adapter.confirmed("synthetic-job-1")).hasMessage("PROVIDER_CALL_INSIDE_TRANSACTION");}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}assertThat(polls.get()).isEqualTo(1);
  }finally{server.stop(0);}
 }
 private FedExAdapter adapter(HttpServer server){return new FedExAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:"+server.getAddress().getPort(),"mock-fedex-client","mock-fedex-secret","mock-fedex-account");}
 @Test void acceptedWriteWithStalledResponseBodyIsBoundedAndUncertain()throws Exception{
  var accepted=new AtomicInteger();var release=new java.util.concurrent.CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{x.getRequestBody().readAllBytes();if(x.getRequestURI().getPath().equals("/oauth/token")){byte[] b=EventCodec.JSON.writeValueAsBytes(Map.of("access_token","mock-token","token_type","bearer","expires_in",3600));x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();}else{accepted.incrementAndGet();x.sendResponseHeaders(200,0);x.getResponseBody().flush();try{release.await(20,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{x.close();}}});server.start();
  try{long start=System.nanoTime();assertThatThrownBy(()->adapter(server).book(EventCodec.JSON.valueToTree(Map.of("requestedShipment",Map.of())))).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.uncertain).isTrue());assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(13));assertThat(accepted.get()).isEqualTo(1);}finally{release.countDown();server.stop(0);}
 }
}
