package com.nigson.commerce.fulfillment;

import java.net.*;
import java.time.*;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import com.nigson.commerce.pricing.CarrierPacking;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

class HttpCarrierRatesTest {
 private CarrierRates.Request request(){var address=new CarrierPacking.Address("Synthetic Recipient","+2348000000001","Synthetic Street",null,"Lagos","LA",null,"NG");return new CarrierRates.Request(UUID.randomUUID(),UUID.randomUUID(),"NG","NGN",address,address,List.of(new CarrierPacking.Parcel(UUID.randomUUID(),"1","1","10","10","10")),null);}
 @Test void sendsOnlyCompiledWorkloadAndAssertionWithClosedServerBody()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var seen=new ArrayList<String>();Instant expires=Instant.now().plusSeconds(250);
  server.createContext("/internal/v1/carrier/quotes",x->{seen.add(x.getRequestHeaders().getFirst("Authorization"));seen.add(x.getRequestHeaders().getFirst("X-Nigson-Actor"));seen.add(new String(x.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));byte[] b=EventCodec.JSON.writeValueAsBytes(new CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","NGN","1234.56",expires,null));x.getResponseHeaders().add("Content-Type","application/json");x.getResponseHeaders().add("X-Nigson-Carrier-Mode","MOCK");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
  try{var client=new HttpCarrierRates("test","http://127.0.0.1:"+server.getAddress().getPort(),route->{assertThat(route.api()).isEqualTo("API-182");return "mock-workload-token";},Clock.systemUTC(),"MOCK");var input=request();assertThat(client.quote(input,"mock-signed-assertion",UUID.randomUUID()).amount()).isEqualTo("1234.56");assertThat(seen.subList(0,2)).containsExactly("Bearer mock-workload-token","mock-signed-assertion");var body=EventCodec.JSON.readTree(seen.get(2));assertThat(body.has("serviceCode")).isFalse();assertThat(body.propertyNames()).containsExactlyInAnyOrder("commandId","cartId","market","currency","shipper","destination","packages");assertThat(HttpCarrierRates.body(input)).isEqualTo(seen.get(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}finally{server.stop(0);}
 }
 @Test void invalidOwnerResponseNeverBecomesNgnQuote()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var mode=new java.util.concurrent.atomic.AtomicReference<>("MOCK");var response=new java.util.concurrent.atomic.AtomicReference<>(new CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","USD","1234.56",Instant.now().plusSeconds(250),null));
  server.createContext("/internal/v1/carrier/quotes",x->{x.getRequestBody().readAllBytes();byte[] b=EventCodec.JSON.writeValueAsBytes(response.get());x.getResponseHeaders().add("Content-Type","application/json");x.getResponseHeaders().add("X-Nigson-Carrier-Mode",mode.get());x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
  try{var client=new HttpCarrierRates("test","http://127.0.0.1:"+server.getAddress().getPort(),r->"mock-workload-token",Clock.systemUTC(),"MOCK");assertThatThrownBy(()->client.quote(request(),"mock-actor",UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_RULE_VIOLATION));response.set(new CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","NGN","12.340",Instant.now().plusSeconds(250),null));assertThatThrownBy(()->client.quote(request(),"mock-actor",UUID.randomUUID())).isInstanceOf(Problem.Failure.class);response.set(new CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","NGN","12.34",Instant.now().plusSeconds(301),null));assertThatThrownBy(()->client.quote(request(),"mock-actor",UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_UNAVAILABLE));response.set(new CarrierRates.Rate("FEDEX","SYNTHETIC_SERVICE","NGN","12.34",Instant.now().plusSeconds(250),null));mode.set("SANDBOX");assertThatThrownBy(()->client.quote(request(),"mock-actor",UUID.randomUUID())).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_MODE_MISMATCH));}finally{server.stop(0);}
 }
 @Test void productionTargetAndTransactionGuardFailBeforeCredentials(){assertThatThrownBy(()->new HttpCarrierRates("prod","http://127.0.0.1:9",r->"mock-token",Clock.systemUTC(),"MOCK")).hasMessage("INTEGRATION_TARGET_REQUIRED");var client=new HttpCarrierRates("test","http://127.0.0.1:9",r->{throw new AssertionError("CREDENTIAL_HTTP_INSIDE_TX");},Clock.systemUTC(),"MOCK");TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->client.quote(request(),"mock-actor",UUID.randomUUID())).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}}
}
