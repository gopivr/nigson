package com.nigson.commerce.platform;
import com.nigson.shared.observability.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.api.common.*;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import java.time.Duration;
import java.net.URI;
import java.util.*;
/** Sole owning SDK provider, without global registration or a duplicate agent. */
public final class Telemetry implements AutoCloseable {
 private final OpenTelemetrySdk sdk;public final Tracer tracer;public final MeterRegistry metrics;
 public Telemetry(MeterRegistry metrics,String env,String endpoint){this(metrics,env,exporter(env,endpoint));}
 public Telemetry(MeterRegistry metrics,String env,SpanExporter exporter){
  this.metrics=metrics;metrics.counter("nigson.http.errors","owner","commerce");var builder=SdkTracerProvider.builder().setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn()).setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"),"nigson-commerce",AttributeKey.stringKey("deployment.environment.name"),env,AttributeKey.stringKey("service.version"),"0.1.0"))).setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(16).setMaxAttributeValueLength(128).setMaxNumberOfEvents(0).setMaxNumberOfLinks(8).build());
  if(exporter!=null)builder.addSpanProcessor(BatchSpanProcessor.builder(exporter).setMaxQueueSize(2048).setMaxExportBatchSize(256).setScheduleDelay(Duration.ofSeconds(1)).setExporterTimeout(Duration.ofSeconds(3)).build());
  sdk=OpenTelemetrySdk.builder().setTracerProvider(builder.build()).build();tracer=sdk.getTracer("nigson-commerce","0.1.0");
 }
 private static SpanExporter exporter(String env,String endpoint){
  if(endpoint==null||endpoint.isBlank())return null;URI uri=URI.create(endpoint);
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new IllegalArgumentException("TELEMETRY_TARGET_INVALID");
  target(env,uri);
  return OtlpHttpSpanExporter.builder().setEndpoint(endpoint.replaceAll("/$","")+"/v1/traces").setTimeout(Duration.ofSeconds(3)).build();
 }
 public static void target(String env,URI uri){
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("TELEMETRY_TARGET_INVALID");
  if(Set.of("local","test").contains(env)){if(!Set.of("otel-collector","127.0.0.1").contains(uri.getHost())||!"http".equals(uri.getScheme()))throw new IllegalArgumentException("LOCAL_TELEMETRY_TARGET_INVALID");return;}
  String policy=System.getenv("NIGSON_TRACE_POLICY_ID");boolean sidecar="http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost())&&uri.getPort()==4318;
  boolean secure="https".equals(uri.getScheme())&&!Set.of("localhost","127.0.0.1").contains(uri.getHost())&&!uri.getHost().endsWith(".example");
  if(policy==null||policy.isBlank()||!(sidecar||secure))throw new IllegalArgumentException("TELEMETRY_POLICY_REQUIRED");
 }
 /** Sampling is owned by the bounded collector, which retains complete linked traces. */
 public Observation http(UUID correlation,String parent,String route,String method){return new Observation(correlation,tracer.spanBuilder("http.request").setSpanKind(SpanKind.SERVER).setParent(WireContext.parent(parent)).setAttribute("http.route",route).setAttribute("http.request.method",method).setAttribute("nigson.priority",route.startsWith("/api/v1/checkout/")).startSpan());}
 public Observation consumer(UUID correlation,String parent,String event){return messaging(correlation,parent,event,SpanKind.CONSUMER,"process","aws_sqs");}
 public Observation producer(UUID correlation,String parent,String event){return messaging(correlation,parent,event,SpanKind.PRODUCER,"publish","aws_sns");}
 private Observation messaging(UUID correlation,String parent,String event,SpanKind kind,String operation,String system){var builder=tracer.spanBuilder("messaging."+operation).setSpanKind(kind).setNoParent().setAttribute("messaging.system",system).setAttribute("messaging.operation.name",operation).setAttribute("nigson.event_type",event);var link=Span.fromContext(WireContext.parent(parent)).getSpanContext();if(link.isValid())builder.addLink(link);return new Observation(correlation,builder.startSpan());}
 public final class Observation implements AutoCloseable {
  public final Span span;private final WireContext wire;private final Map<String,String> previous;
  Observation(UUID correlation,Span span){this.span=span;previous=MDC.getCopyOfContextMap();wire=new WireContext(correlation,tracer,Context.root().with(span));MDC.put("correlation_id",correlation.toString());MDC.put("trace_id",span.getSpanContext().getTraceId());MDC.put("span_id",span.getSpanContext().getSpanId());}
  public void failed(String code){span.setStatus(StatusCode.ERROR);MDC.put("error_code",code);}
  public void close(){try{span.end();}finally{wire.close();if(previous==null)MDC.clear();else MDC.setContextMap(previous);}}
 }
 public void flush(){sdk.getSdkTracerProvider().forceFlush().join(5,java.util.concurrent.TimeUnit.SECONDS);}public void close(){sdk.close();}
}
