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

 private static TermiiTokenAdapter termii(HttpServer s){return new TermiiTokenAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:"+s.getAddress().getPort(),"mock-termii-key","Nigson");}

 private static void reply(com.sun.net.httpserver.HttpExchange e,Object value)throws java.io.IOException{byte[] data=EventCodec.JSON.writeValueAsBytes(value);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,data.length);try(var out=e.getResponseBody()){out.write(data);}}
}
