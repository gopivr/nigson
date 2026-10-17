package com.nigson.commerce.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.nigson.shared.problem.Problem;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;

class TwilioStatusIngressTest {
 private static final String TOKEN="local-twilio-auth-token-123456";
 @Test void validatesFullTwilioSignatureAndRelaysOnlyMinimizedReceipt() throws Exception {
  UUID job=UUID.randomUUID();String sid="SM"+"a".repeat(32);Map<String,String> params=new TreeMap<>();params.put("AccountSid","AC"+"b".repeat(32));params.put("MessageSid",sid);params.put("MessageStatus","delivered");params.put("To","+2348012345678");params.put("From","+15005550006");params.put("Body","private verification code 123456");String form=encode(params);
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var received=new java.util.concurrent.atomic.AtomicReference<String>();var auth=new java.util.concurrent.atomic.AtomicReference<String>();var correlationHeader=new java.util.concurrent.atomic.AtomicReference<String>();server.createContext("/internal/v1/notification-provider-receipts",exchange->{received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));correlationHeader.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));byte[] response="{\"acknowledged\":true}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
  try {String base="http://127.0.0.1:"+server.getAddress().getPort();var ingress=new TwilioStatusIngress("test",params.get("AccountSid"),TOKEN,"http://localhost:8080/api/v1/webhooks/notifications/twilio/status",base,r->"eyJ.test.signature",Clock.systemUTC());String exact="http://localhost:8080/api/v1/webhooks/notifications/twilio/status?jobId="+job;UUID correlation=UUID.randomUUID();ingress.receive(job,form.getBytes(StandardCharsets.US_ASCII),sign(exact,params),correlation);assertThat(auth.get()).isEqualTo("Bearer eyJ.test.signature");assertThat(correlationHeader.get()).isEqualTo(correlation.toString());assertThat(received.get()).contains(job.toString(),sid,params.get("AccountSid"),"delivered").doesNotContain("+2348012345678","private verification code","15005550006");
   assertThatThrownBy(()->ingress.receive(job,form.getBytes(StandardCharsets.US_ASCII),"invalid",correlation)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->ingress.receive(job,(form+"&MessageStatus=failed").getBytes(StandardCharsets.US_ASCII),sign(exact,params),correlation)).isInstanceOf(Problem.Failure.class);var missingStatus=new TreeMap<>(params);missingStatus.remove("MessageStatus");assertThatThrownBy(()->ingress.receive(job,encode(missingStatus).getBytes(StandardCharsets.US_ASCII),sign(exact,missingStatus),correlation)).isInstanceOf(Problem.Failure.class);
  } finally {server.stop(0);}
 }
 private static String encode(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
 private static String sign(String url,Map<String,String> values)throws Exception{var data=new StringBuilder(url);new TreeMap<>(values).forEach((k,v)->data.append(k).append(v));var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(TOKEN.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));}
}
