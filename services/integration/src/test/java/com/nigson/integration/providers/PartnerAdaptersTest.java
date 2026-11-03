package com.nigson.integration.providers;

import com.sun.net.httpserver.HttpServer;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ProviderHttp;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.*;import java.time.*;import java.util.*;import java.util.concurrent.atomic.*;import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/** Stateful actual HTTP protocol checks; no paid SMS or carrier calls. */
class PartnerAdaptersTest {
 @Test void termiiOtpUsesTransactionalRouteAndVerifiesTheSameChallenge()throws Exception{
  var sends=new AtomicInteger();var accepted=new AtomicBoolean();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{assertThat(e.getRequestHeaders().getFirst("Accept")).isEqualTo("application/json");assertThat(e.getRequestHeaders().getFirst("User-Agent")).isEqualTo("Nigson-provider/1.0");var body=EventCodec.JSON.readTree(e.getRequestBody().readAllBytes());assertThat(body.path("api_key").asString()).isEqualTo("mock-termii-key");Object result;
   if(e.getRequestURI().getPath().equals("/api/sms/otp/send")){sends.incrementAndGet();accepted.set(true);assertThat(body.path("channel").asString()).isEqualTo("dnd");assertThat(body.path("to").asString()).isEqualTo("2348012345678");assertThat(body.path("pin_length").asInt()).isEqualTo(6);result=Map.of("pinId","mock-pin-id","smsStatus","Message Sent");}
   else{assertThat(accepted.get()).isTrue();assertThat(body.path("pin_id").asString()).isEqualTo("mock-pin-id");result=Map.of("pinId","mock-pin-id","verified",body.path("pin").asString().equals("123456")?"True":"False");}
   reply(e,result);});server.start();try{var adapter=termii(server);var challenge=adapter.send("+2348012345678",5);assertThat(challenge.pinId()).isEqualTo("mock-pin-id");assertThat(adapter.verify(challenge.pinId(),"000000")).isFalse();assertThat(adapter.verify(challenge.pinId(),"123456")).isTrue();assertThat(sends.get()).isEqualTo(1);}finally{server.stop(0);}
 }
 @Test void uncertainTermiiSendIsNeverAutomaticallyRetried()throws Exception{
  var sends=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{e.getRequestBody().readAllBytes();sends.incrementAndGet();reply(e,Map.of("accepted",true));});server.start();try{assertThatThrownBy(()->termii(server).send("+2348012345678",5)).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.uncertain).isTrue());assertThat(sends.get()).isEqualTo(1);}finally{server.stop(0);}
 }
 @Test void dellymanQuotesBookingsAndSignedRawEventsKeepProviderIdentity()throws Exception{
  var stored=new HashMap<String,String>();var writes=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer mock-dellyman-key");var body=EventCodec.JSON.readTree(e.getRequestBody().readAllBytes());Object result;
   switch(e.getRequestURI().getPath()){
    case "/api/v3.0/GetQuotes"->{assertThat(body.path("PaymentMode").asString()).isEqualTo("online");assertThat(body.path("PickupRequestedDate").asString()).isEqualTo("2026/11/04");result=Map.of("ResponseCode",100,"Companies",List.of(Map.of("CompanyID",643,"Name","Mock Carrier","PayableAmount",1584)));}
    case "/api/v3.0/BookOrder"->{writes.incrementAndGet();stored.putIfAbsent(body.path("OrderRef").asString(),"5585");result=Map.of("ResponseCode",100,"OrderID",5585,"TrackingID","483632023805");}
    default->{assertThat(body.path("OrderID").asString()).isEqualTo("5585");result=Map.of("OrderID","5585","OrderStatus","PENDING");}
   }reply(e,result);});server.start();try{
   var adapter=dellyman(server);assertThat(adapter.quotes("Bike",LocalDate.of(2026,11,4),"08:00 AM to 05:00 PM","Mock pickup","Mock delivery").getFirst().amount()).isEqualByComparingTo("1584.00");
   UUID stable=UUID.randomUUID();var booking=book(adapter,stable);assertThat(booking.orderId()).isEqualTo("5585");assertThat(stored).containsOnlyKeys(stable.toString());assertThat(writes.get()).isEqualTo(1);assertThat(adapter.order(booking.orderId()).path("OrderStatus").asString()).isEqualTo("PENDING");
   byte[] raw="{\"order\":{\"OrderID\":5585}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("mock-webhook-secret".getBytes(),"HmacSHA256"));String signature=HexFormat.of().formatHex(mac.doFinal(raw));assertThat(adapter.authenticWebhook(raw,signature)).isTrue();assertThat(adapter.authenticWebhook("{}".getBytes(),signature)).isFalse();assertThat(adapter.authenticWebhook(raw,"00")).isFalse();
  }finally{server.stop(0);}
 }
 @Test void ambiguousBookingAndKnownRejectionAreDistinct()throws Exception{
  var writes=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{e.getRequestBody().readAllBytes();writes.incrementAndGet();reply(e,writes.get()==1?Map.of("ResponseCode",100):Map.of("ResponseCode",403,"ResponseMessage","Rejected"));});server.start();try{var adapter=dellyman(server);assertThatThrownBy(()->book(adapter,UUID.randomUUID())).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.uncertain).isTrue());assertThat(writes.get()).isEqualTo(1);assertThatThrownBy(()->adapter.quotes("Bike",LocalDate.of(2026,11,4),"08:00 AM to 05:00 PM","Mock pickup","Mock delivery")).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.uncertain).isFalse());}finally{server.stop(0);}
 }
 @Test void credentialsModesAndTransactionBoundariesFailClosed()throws Exception{
  assertThatThrownBy(()->new DellymanAdapter("prod",ProviderHttp.Mode.MOCK,"http://dellyman-mock:8080","mock-key","mock-secret")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new DellymanAdapter("test",ProviderHttp.Mode.TEST,"https://dellyman.com","real-key","real-secret")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new TermiiTokenAdapter("test",ProviderHttp.Mode.LIVE,"https://untrusted.invalid","real-key","Nigson")).isInstanceOf(IllegalArgumentException.class);
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();try{TransactionSynchronizationManager.setActualTransactionActive(true);assertThatThrownBy(()->termii(server).send("+2348012345678",5)).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->book(dellyman(server),UUID.randomUUID())).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);server.stop(0);}
 }
 private static TermiiTokenAdapter termii(HttpServer s){return new TermiiTokenAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:"+s.getAddress().getPort(),"mock-termii-key","Nigson");}
 private static DellymanAdapter dellyman(HttpServer s){return new DellymanAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:"+s.getAddress().getPort(),"mock-dellyman-key","mock-webhook-secret");}
 private static DellymanAdapter.Booking book(DellymanAdapter a,UUID ref){return a.book(ref,"643","Bike","Mock pickup","08012345678","Mock pickup address","Mock landmark",LocalDate.of(2026,11,4),"08:00 AM to 05:00 PM","08:00 AM to 05:00 PM","sameDay","Mock electronics","Mock recipient","08012345679","Mock delivery address","Mock landmark",new BigDecimal("2"));}
 private static void reply(com.sun.net.httpserver.HttpExchange e,Object value)throws java.io.IOException{byte[] data=EventCodec.JSON.writeValueAsBytes(value);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,data.length);try(var out=e.getResponseBody()){out.write(data);}}
}
