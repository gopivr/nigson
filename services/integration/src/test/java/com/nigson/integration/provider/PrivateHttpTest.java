package com.nigson.integration.provider;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PrivateHttpTest {
 @Test void deadlineBoundsStalledBodyAndSizeCapRejectsOversizedResponse()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   server.setExecutor(pool);server.createContext("/stall",e->{e.sendResponseHeaders(200,0);try(var out=e.getResponseBody()){out.write(1);out.flush();try{Thread.sleep(1200);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}catch(java.io.IOException cancelled){e.close();}});server.createContext("/large",e->{byte[] bytes=new byte[1025];e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();try{
    var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();String base="http://127.0.0.1:"+server.getAddress().getPort();long start=System.nanoTime();assertThatThrownBy(()->PrivateHttp.send(client,HttpRequest.newBuilder(URI.create(base+"/stall")).GET().build(),1024,Duration.ofMillis(250))).hasMessage("PRIVATE_HTTP_UNAVAILABLE");assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));assertThatThrownBy(()->PrivateHttp.send(client,HttpRequest.newBuilder(URI.create(base+"/large")).GET().build(),1024,Duration.ofSeconds(2))).hasMessage("PRIVATE_HTTP_UNAVAILABLE");
   }finally{server.stop(0);}
  }
 }
 @Test void providerTargetsAndMetricLabelsFailClosed(){
  assertThatThrownBy(()->new PaymentReconciliationHandler("prod","http://127.0.0.1:1",25,r->"synthetic")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new PaymentReconciliationHandler("stage","https://caller.example.invalid",25,r->"synthetic")).isInstanceOf(IllegalArgumentException.class);
  var registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();registry.config().meterFilter(new com.nigson.integration.platform.MetricPolicy());registry.counter("nigson.integration.jobs","owner","integration","kind","PAYMENT_RECONCILE","state","SUCCEEDED").increment();assertThat(registry.get("nigson.integration.jobs").counter().count()).isEqualTo(1);registry.counter("nigson.integration.jobs","resource_id","synthetic-sensitive-id").increment();assertThat(registry.find("nigson.integration.jobs").tag("resource_id","synthetic-sensitive-id").counter()).isNull();
 }
}
