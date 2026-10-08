package com.nigson.integration;
import com.nigson.integration.platform.*;
import com.nigson.shared.observability.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.*;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import jakarta.servlet.http.*;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.api.trace.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SafeBoundaryTest {
 @Test void actualHttpRejectsSafelyAndLinksValidatedContext()throws Exception{
  var exporter=InMemorySpanExporter.create();var meters=new SimpleMeterRegistry();meters.config().meterFilter(new MetricPolicy());
  try(var telemetry=new Telemetry(meters,"test",exporter)){
   var problems=new ProblemWriter("test","");var boundary=new RequestBoundary(telemetry,problems);var factory=new TomcatServletWebServerFactory(0);
   var server=factory.getWebServer(context->{
    var filter=context.addFilter("request-boundary",boundary);filter.addMappingForUrlPatterns(EnumSet.of(jakarta.servlet.DispatcherType.REQUEST),false,"/*");
    var servlet=context.addServlet("test-only-failing-command",new HttpServlet(){protected void doGet(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{problems.write(request,response,problems.classify(new org.springframework.security.access.AccessDeniedException("private@example.invalid sk_private_fixture")),List.of());}protected void doPost(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{problems.write(request,response,problems.classify(new org.springframework.dao.DataIntegrityViolationException("SELECT private_token FROM guarded_table")),List.of());}});servlet.addMapping("/*");
   });server.start();
   try(var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()){
    String parent="00-0123456789abcdef0123456789abcdef-0123456789abcdef-00";UUID correlation=UUID.randomUUID();String base="http://127.0.0.1:"+server.getPort();
    var response=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/orders/"+UUID.randomUUID()+"?email=private@example.invalid")).header("X-Correlation-ID",correlation.toString()).header("traceparent",parent).GET().build(),HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(403);assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");assertThat(response.headers().firstValue("X-Correlation-ID").orElseThrow()).isEqualTo(correlation.toString());
    var body=EventCodec.JSON.readTree(response.body());assertThat(body.path("code").asString()).isEqualTo("ACCESS_DENIED");assertThat(body.path("traceId").asString()).isEqualTo("0123456789abcdef0123456789abcdef");assertThat(body.path("instance").asString()).isEqualTo("/api/v1/orders/_id");assertThat(body.path("timestamp").isString()).isTrue();assertThat(response.body()).doesNotContain("private@example","sk_private_fixture","AccessDeniedException");
    var conflict=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/checkout/attempts")).POST(HttpRequest.BodyPublishers.ofString("{}" )).build(),HttpResponse.BodyHandlers.ofString());assertThat(conflict.statusCode()).isEqualTo(409);assertThat(conflict.body()).doesNotContain("SELECT","guarded_table");
    var oversized=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/webhooks/payments/paystack")).POST(HttpRequest.BodyPublishers.ofString("x".repeat(262145))).build(),HttpResponse.BodyHandlers.ofString());assertThat(oversized.statusCode()).isEqualTo(413);
    for(int i=0;i<30;i++)http.send(HttpRequest.newBuilder(URI.create(base+"/unregistered-"+UUID.randomUUID())).header("X-Correlation-ID","private@example.invalid").header("traceparent","not-a-trace").GET().build(),HttpResponse.BodyHandlers.discarding());
    telemetry.flush();var spans=exporter.getFinishedSpanItems();assertThat(spans).anySatisfy(span->{assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);assertThat(span.getParentSpanId()).isEqualTo("0123456789abcdef");assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);});
    assertThat(meters.get("nigson.http.server.requests").timers()).hasSize(4);assertThat(WireContext.current()).isNull();
   }finally{server.stop();server.destroy();}
  }
 }
 @Test void boundedValuesAndAuditRedactionFailClosed(){
  assertThatThrownBy(()->ApiValues.uuid("00000000-0000-0000-0000-000000000000")).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->ApiValues.money("123.45678",true)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->ApiValues.quantity("0")).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->ApiValues.idempotencyKey("key\nprivate" )).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->ApiValues.version("W/\"1\"")).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->ApiValues.page(0,101)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->ApiValues.query(Map.of("phone","private"),Set.of("page"))).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->ApiValues.jsonShape("{\"private\":1,\"private\":2}".getBytes(),100)).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->ApiValues.jsonShape(("["+"0,".repeat(100)+"0]").getBytes(),100)).isInstanceOf(Problem.Failure.class);
  ApiValues.jsonShape(("["+"0,".repeat(100)+"0]").getBytes(),500);
  var safe=AuditValues.redact(Map.of("version",3,"password","private-password","email","private@example.invalid","state","PAID"));assertThat(safe.toString()).doesNotContain("private","email","password");assertThat(safe.path("redacted").asBoolean()).isTrue();
  assertThatThrownBy(()->AuditValues.redact(Map.of("role","PAID"))).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->AuditValues.ipFingerprint(new byte[32],"deadbeef" )).isInstanceOf(Problem.Failure.class);
  assertThat(AuditValues.ipFingerprint(new byte[32],"127.0.0.1")).hasSize(64);
 }
 @Test void logProtocolRejectsRawMessagesThrowableArgumentsAndUnregisteredMeters(){
  var encoder=new SafeJsonEncoder();encoder.setService("nigson-integration");encoder.setEnvironment("test");encoder.setVersion("0.1.0");
  var event=new ch.qos.logback.classic.spi.LoggingEvent();event.setTimeStamp(System.currentTimeMillis());event.setLevel(ch.qos.logback.classic.Level.ERROR);event.setMessage("private@example.invalid {} SELECT secret");event.setArgumentArray(new Object[]{"private-token"});event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(new RuntimeException("private-token")));event.setMDCPropertyMap(Map.of("error_code","PRIVATE_TOKEN","correlation_id",UUID.randomUUID().toString()));
  String line=new String(encoder.encode(event),java.nio.charset.StandardCharsets.UTF_8);var body=EventCodec.JSON.readTree(line);assertThat(line).doesNotContain("private","PRIVATE_TOKEN","SELECT","RuntimeException");assertThat(body.path("message").asString()).isEqualTo("OPERATOR_EVENT");assertThat(body.path("error_code").isNull()).isTrue();assertThat(body.size()).isEqualTo(10);
  var registry=new SimpleMeterRegistry();registry.config().meterFilter(new MetricPolicy());registry.counter("nigson.attacker."+UUID.randomUUID()).increment();registry.counter("nigson.http.errors","request_id",UUID.randomUUID().toString()).increment();assertThat(registry.getMeters()).isEmpty();
 }

}
