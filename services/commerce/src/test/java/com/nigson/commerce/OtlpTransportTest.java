package com.nigson.commerce;
import com.nigson.commerce.platform.*;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.*;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class OtlpTransportTest {
 private static final class Registry extends OtlpMeterRegistry implements AutoCloseable {Registry(OtlpConfig config){super(config,Clock.SYSTEM);stop();}void send(){publish();}}
 @Test void actualOtlpHttpCarriesSafeSpansAndExactAlarmMetricNames()throws Exception {
  var traces=new LinkedBlockingQueue<byte[]>();var metrics=new LinkedBlockingQueue<byte[]>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/traces",exchange->{traces.add(exchange.getRequestBody().readAllBytes());exchange.sendResponseHeaders(200,0);exchange.close();});server.createContext("/v1/metrics",exchange->{metrics.add(exchange.getRequestBody().readAllBytes());exchange.sendResponseHeaders(200,0);exchange.close();});server.start();String endpoint="http://127.0.0.1:"+server.getAddress().getPort();
  try(var registry=new Registry(new OtlpConfig(){public String get(String key){return null;}public String url(){return endpoint+"/v1/metrics";}});var sdk=new Telemetry(registry,"test",endpoint)){
   registry.config().meterFilter(new MetricPolicy());registry.counter("nigson.http.requests","owner","commerce").increment(2);registry.counter("nigson.http.errors","owner","commerce").increment();
   try(var observed=sdk.http(UUID.randomUUID(),null,"/api/v1/checkout/attempts","POST")){observed.failed("ACCESS_DENIED");}
   sdk.flush();registry.send();
   var wire=ExportTraceServiceRequest.parseFrom(traces.poll(5,TimeUnit.SECONDS));var spans=wire.getResourceSpansList().stream().flatMap(r->r.getScopeSpansList().stream()).flatMap(s->s.getSpansList().stream()).toList();assertThat(spans).hasSize(1);assertThat(spans.getFirst().getName()).isEqualTo("http.request");assertThat(spans.getFirst().getEventsCount()).isZero();assertThat(spans.getFirst().getAttributesList().toString()).doesNotContain("http.url","db.statement","token");
   var metricWire=ExportMetricsServiceRequest.parseFrom(metrics.poll(5,TimeUnit.SECONDS));var names=metricWire.getResourceMetricsList().stream().flatMap(r->r.getScopeMetricsList().stream()).flatMap(s->s.getMetricsList().stream()).map(m->m.getName()).toList();assertThat(names).contains("nigson.http.requests","nigson.http.errors");
  }finally{server.stop(0);}
 }
}
