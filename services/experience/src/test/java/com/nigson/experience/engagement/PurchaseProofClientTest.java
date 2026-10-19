package com.nigson.experience.engagement;

import com.nigson.shared.events.EventCodec;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PurchaseProofClientTest {
 @Test void localStatefulCommerceHttpMockBindsProofTupleAndPublicMarketLookup()throws Exception{
  UUID market=UUID.randomUUID(),party=UUID.randomUUID(),line=UUID.fromString("22222222-2222-4222-8222-222222222222"),product=UUID.randomUUID();var http=new StatefulCommerceMock(List.of(
   new Reply(200,EventCodec.JSON.writeValueAsBytes(List.of(Map.of("id",market,"code","NG","currency","NGN","destinations",List.of("NG"),"policyRevision",1)))),
   new Reply(200,EventCodec.JSON.writeValueAsBytes(List.of(Map.of("id",market,"code","NG","currency","NGN","destinations",List.of("NG"),"policyRevision",1)))),
   new Reply(200,EventCodec.JSON.writeValueAsBytes(List.of(Map.of("id",market,"code","NG","currency","NGN","destinations",List.of("NG"),"policyRevision",1)))),
   new Reply(201,EventCodec.JSON.writeValueAsBytes(Map.of("eligible",true,"reasonCode","PURCHASE_VERIFIED","revision",1))),
   new Reply(201,EventCodec.JSON.writeValueAsBytes(Map.of("eligible",false,"reasonCode","NO_QUALIFYING_PURCHASE","revision",1)))));
  var client=new PurchaseProofClient(URI.create("https://commerce.internal"),route->"local-stateful-workload-token",http);
  assertThat(client.active(market)).isTrue();assertThat(client.resolve("NG")).isEqualTo(market);assertThat(client.resolve("XX")).isNull();
  assertThat(client.eligible(party,line,product)).isTrue();assertThat(client.eligible(party,UUID.fromString("11111111-1111-4111-8111-111111111111"),product)).isFalse();
  assertThat(http.requests).hasSize(5);var request=http.requests.get(3);assertThat(request.uri().getPath()).isEqualTo("/internal/v1/purchase-proofs");assertThat(request.method()).isEqualTo("POST");assertThat(request.headers().firstValue("Authorization")).contains("Bearer local-stateful-workload-token");assertThat(EventCodec.JSON.readTree(requestBody(request))).isEqualTo(EventCodec.JSON.readTree(EventCodec.JSON.writeValueAsBytes(Map.of("partyId",party,"orderLineId",line,"productId",product))));
 }
 private static byte[] requestBody(HttpRequest request)throws Exception{var out=new java.io.ByteArrayOutputStream();var done=new CountDownLatch(1);request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<java.nio.ByteBuffer>(){public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}public void onNext(java.nio.ByteBuffer b){byte[] data=new byte[b.remaining()];b.get(data);out.writeBytes(data);}public void onError(Throwable e){done.countDown();}public void onComplete(){done.countDown();}});if(!done.await(2,TimeUnit.SECONDS))throw new AssertionError("BODY_NOT_PUBLISHED");return out.toByteArray();}
 private record Reply(int status,byte[] body){}
 private static final class StatefulCommerceMock extends HttpClient{
  private final Queue<Reply> replies;private final List<HttpRequest> requests=new ArrayList<>();StatefulCommerceMock(List<Reply> replies){this.replies=new ArrayDeque<>(replies);}
  @SuppressWarnings("unchecked") public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler){requests.add(request);var reply=replies.remove();return (HttpResponse<T>)new Response(request,reply.status(),reply.body());}
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){return CompletableFuture.failedFuture(new UnsupportedOperationException());}
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return CompletableFuture.failedFuture(new UnsupportedOperationException());}
  public Optional<java.net.CookieHandler> cookieHandler(){return Optional.empty();}public Optional<Duration> connectTimeout(){return Optional.empty();}public Redirect followRedirects(){return Redirect.NEVER;}public Optional<ProxySelector> proxy(){return Optional.empty();}public SSLContext sslContext(){return null;}public SSLParameters sslParameters(){return new SSLParameters();}public Optional<Authenticator> authenticator(){return Optional.empty();}public Version version(){return Version.HTTP_1_1;}public Optional<Executor> executor(){return Optional.empty();}
 }
 private record Response(HttpRequest request,int statusCode,byte[] bytes) implements HttpResponse<byte[]>{public int statusCode(){return statusCode;}public HttpRequest request(){return request;}public Optional<HttpResponse<byte[]>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(a,b)->true);}public byte[] body(){return bytes;}public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}}
}
