package com.nigson.shared.security;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Bounded transport only. Owners interpret provider contracts and persist intent/outcome. */
public final class ProviderHttp {
 public enum Mode { MOCK, TEST, LIVE }
 public record Result(int status,String body) {}
 public static final class Failure extends RuntimeException {
  public final boolean uncertain;public final int status;
  public Failure(String code,boolean uncertain,int status){super(code);this.uncertain=uncertain;this.status=status;}
 }
 private final URI base;
 private final String provider;private final Mode mode;
 private final HttpClient client;
 public ProviderHttp(String environment,Mode mode,String endpoint,String mockHost,Set<String> officialHosts,String... credentials) {
  if(endpoint==null||endpoint.length()>2048||mode==null)throw new IllegalArgumentException("PROVIDER_TARGET_INVALID");
  base=URI.create(endpoint);this.provider=mockHost.replace("-mock","");this.mode=mode;
  if(base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!Set.of("","/").contains(base.getPath()))throw new IllegalArgumentException("PROVIDER_TARGET_INVALID");
  boolean local=Set.of("local","test").contains(environment);
  if(mode==Mode.MOCK){if(!local||!base.getScheme().equals("http")||!(base.getHost().equals(mockHost)&&base.getPort()==8080||environment.equals("test")&&base.getHost().equals("127.0.0.1")&&base.getPort()>0))throw new IllegalArgumentException("MOCK_TARGET_INVALID");}
  else if(!base.getScheme().equals("https")||!officialHosts.contains(base.getHost())||base.getPort()!=-1)throw new IllegalArgumentException("PROVIDER_TARGET_INVALID");
  if(environment.equals("prod")&&mode!=Mode.LIVE)throw new IllegalArgumentException("PRODUCTION_MODE_INVALID");
  if(!Set.of("local","test","dev","qa","stage","prod").contains(environment))throw new IllegalArgumentException("ENVIRONMENT_INVALID");
  for(String key:credentials){if(key==null||key.isBlank()||key.length()>2048)throw new IllegalArgumentException("PROVIDER_CREDENTIAL_MISSING");boolean dummy=key.toLowerCase(Locale.ROOT).matches(".*(mock|dummy|placeholder|example).*" )||key.matches("(?:AC|MG|HX)?0{8,}");if(mode==Mode.MOCK&&!dummy||mode!=Mode.MOCK&&dummy)throw new IllegalArgumentException("PROVIDER_CREDENTIAL_MODE_INVALID");}
  client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 }
 public Result call(String method,String path,String contentType,String body,String authorization) {return call(method,path,contentType,body,authorization,!method.equals("GET"));}
 public Result call(String method,String path,String contentType,String body,String authorization,boolean sideEffect) {
  if(!Set.of("GET","POST","PUT","DELETE").contains(method)||!path.startsWith("/")||path.startsWith("//")||path.contains("..")||body.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("PROVIDER_REQUEST_INVALID");
  URI target=base.resolve(path);if(!Objects.equals(target.getAuthority(),base.getAuthority()))throw new IllegalArgumentException("PROVIDER_TARGET_INVALID");
  var wire=com.nigson.shared.observability.WireContext.current();
  var span=wire==null?io.opentelemetry.api.trace.Span.getInvalid():wire.tracer.spanBuilder("http.provider").setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT).setAttribute("http.request.method",method).setAttribute("nigson.provider",provider).setAttribute("nigson.provider_mode",mode.name()).startSpan();
  java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending=null;
  try(var scope=span.makeCurrent()){
   var builder=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(8)).header("Content-Type",contentType);
   if(authorization!=null&&!authorization.isBlank())builder.header("Authorization",authorization);
   if(wire!=null){builder.header("X-Correlation-ID",wire.correlationId.toString());String trace=com.nigson.shared.observability.WireContext.traceparent();if(trace!=null)builder.header("traceparent",trace);}
   var request=builder.method(method,body.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
   // A request timeout alone may end at response headers; include the entire bounded body.
   pending=client.sendAsync(request,info->new BoundedBody());
   var response=pending.get(9,java.util.concurrent.TimeUnit.SECONDS);
   span.setAttribute("http.response.status_code",response.statusCode());
   if(response.statusCode()<200||response.statusCode()>=300)throw new Failure("PROVIDER_HTTP_"+response.statusCode(),sideEffect&&(response.statusCode()>=500||response.statusCode()==408||response.statusCode()>=300&&response.statusCode()<400),response.statusCode());
   return new Result(response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));
  } catch(Failure classified){span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);throw classified;}catch(InterruptedException interrupted){span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);Thread.currentThread().interrupt();throw new Failure("PROVIDER_INTERRUPTED",sideEffect,0);}catch(Exception transport){span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);throw new Failure("PROVIDER_TRANSPORT_UNCERTAIN",sideEffect,0);}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);span.end();}
 }
 private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
  private java.util.concurrent.Flow.Subscription subscription;private int size;
  public java.util.concurrent.CompletionStage<byte[]> getBody(){return delegate.getBody();}
  public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
  public void onNext(List<java.nio.ByteBuffer> buffers){for(var b:buffers){size+=b.remaining();if(size>1048576){subscription.cancel();delegate.onError(new java.io.IOException("RESPONSE_TOO_LARGE"));return;}}delegate.onNext(buffers);}
  public void onError(Throwable t){delegate.onError(t);}public void onComplete(){delegate.onComplete();}
 }
 public static String form(Map<String,String> fields){return fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
}
