package com.nigson.acceptance.notification;
import com.nigson.commerce.notification.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TemplateTransportTest {
 @Test void malformedAcknowledgementsStayUnknownAndRemoteErrorsAreSafeWithoutAutomaticRetry()throws Exception{
  var mode=new AtomicInteger();var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/v1/templates",x->{calls.incrementAndGet();assertThat(x.getRequestHeaders().getFirst("Cookie")).isNull();assertThat(x.getRequestHeaders().getFirst("X-Nigson-Actor")).isEqualTo("synthetic-assertion");String body=switch(mode.get()){case 0->"x".repeat(65537);case 1->"{\"id\":\""+UUID.randomUUID()+"\",\"code\":\"ORDER_PAID\",\"revision\":1,\"active\":true}";case 2->"{\"id\":\"invalid\",\"code\":\"ORDER_PAID\",\"revision\":1,\"active\":true}";case 3->"{\"code\":\"NOTIFICATION_RULE_VIOLATION\",\"detail\":\"protected provider text\"}";default->"protected redirect";};x.getResponseHeaders().set("Content-Type",mode.get()==3?"application/problem+json":"application/json");if(mode.get()!=1)x.getResponseHeaders().set("ETag","\"1\"");x.getResponseHeaders().set("Location","https://external.invalid");byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);x.sendResponseHeaders(mode.get()==3?422:mode.get()==4?302:201,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
  try{var adapter=new HttpTemplatePort("test","http://127.0.0.1:"+server.getAddress().getPort(),r->{assertThat(r.scope()).isEqualTo("templates.write_WITH_SIGNED_ADMIN");return "synthetic-workload";});var value=TemplateValues.template(NotificationValuesTest.template(),1);for(int i=0;i<5;i++){mode.set(i);assertThatThrownBy(()->adapter.put(value,0L,UUID.randomUUID().toString(),"synthetic-assertion",UUID.randomUUID())).hasMessage(i==3?"NOTIFICATION_RULE_VIOLATION":"PROVIDER_OUTCOME_UNKNOWN");}assertThat(calls.get()).isEqualTo(5);}finally{server.stop(0);}
 }
 @Test void targetConfigurationFailsClosedAndTransactionsPreventCredentialAcquisition(){
  var calls=new AtomicInteger();var port=new HttpTemplatePort("test","http://127.0.0.1:9",r->{calls.incrementAndGet();return "synthetic";});org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->port.put(TemplateValues.template(NotificationValuesTest.template(),1),null,UUID.randomUUID().toString(),"synthetic-assertion",UUID.randomUUID())).hasMessage("IDENTITY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");assertThat(calls.get()).isZero();}finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
  for(String target:List.of("http://127.0.0.1:9","https://example.com","https://user@approved.internal","https://approved.internal/path"))assertThatThrownBy(()->new HttpTemplatePort("prod",target,r->"synthetic")).hasMessage("INTEGRATION_TARGET_REQUIRED");
  assertThatThrownBy(()->new TemplateConfiguration().templatePort(new org.springframework.mock.env.MockEnvironment().withProperty("NIGSON_NOTIFICATION_TEMPLATES_ENABLED","maybe"),null)).isInstanceOf(IllegalArgumentException.class);
 }
}
