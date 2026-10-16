package com.nigson.commerce.fulfillment;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import com.nigson.shared.security.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
/** Closed, bounded original-intent exchange; retries cannot allocate another source request ID. */
public final class HttpCarrierCancellation implements CarrierCancellationPort {
 private static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-198")&&r.owner().equals("integration")&&r.client().equals("nigson-commerce")).findFirst().orElseThrow();
 private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final URI base;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;
 public HttpCarrierCancellation(String env,String target,Function<PrivateWorkload.Route,String> credentials){base=com.nigson.commerce.security.IntegrationTarget.of(env,target);this.credentials=Objects.requireNonNull(credentials);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();}
 public CarrierCancellationValues.Acknowledgement exchange(CarrierCancellationValues.Command input,UUID correlation){com.nigson.commerce.security.IdentityKeys.outsideTransaction();Objects.requireNonNull(input);ApiValues.uuid(correlation==null?null:correlation.toString());CompletableFuture<HttpResponse<byte[]>> future=null;
  try{String token=credentials.apply(ROUTE);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw new IllegalArgumentException();var request=HttpRequest.newBuilder(base.resolve(ROUTE.path())).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Accept","application/json").header("Content-Type","application/json").header("X-Correlation-ID",correlation.toString()).header("Idempotency-Key",input.cancellationId().toString()).POST(HttpRequest.BodyPublishers.ofByteArray(EventCodec.JSON.writeValueAsBytes(input))).build();future=http.sendAsync(request,info->new Limited());var response=future.get(6,TimeUnit.SECONDS);if(response.statusCode()!=202||response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))throw new IllegalArgumentException();var body=JSON.readTree(response.body());var fields=new HashSet<>(Set.of("cancellationId","shipmentId","state"));if(body.has("receipt"))fields.add("receipt");if(!body.isObject()||!body.propertyNames().equals(fields))throw new IllegalArgumentException();for(String field:List.of("cancellationId","shipmentId","state"))if(!body.path(field).isString())throw new IllegalArgumentException();var receipt=body.has("receipt")&&!body.path("receipt").isNull()?CarrierCancellationValues.receipt(body.path("receipt")):null;var result=new CarrierCancellationValues.Acknowledgement(ApiValues.uuid(body.path("cancellationId").asString()),ApiValues.uuid(body.path("shipmentId").asString()),body.path("state").asString(),receipt);if(!input.cancellationId().equals(result.cancellationId())||!input.shipmentId().equals(result.shipmentId())||receipt!=null&&(!input.bookingRequestId().equals(receipt.bookingRequestId())||!input.reference().equals(receipt.reference())||!input.mode().equals(receipt.mode())))throw new IllegalArgumentException();return result;
  }catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}finally{if(future!=null&&!future.isDone())future.cancel(true);}
 }
 private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
  private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private java.util.concurrent.Flow.Subscription subscription;private int size;private boolean failed;
  public CompletionStage<byte[]> getBody(){return delegate.getBody();}public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}public void onNext(List<ByteBuffer> buffers){if(failed)return;for(var buffer:buffers){size+=buffer.remaining();if(size>65536){failed=true;subscription.cancel();delegate.onError(new IllegalStateException("CARRIER_CANCEL_RESPONSE_LIMIT"));return;}}delegate.onNext(buffers);}public void onError(Throwable failure){delegate.onError(failure);}public void onComplete(){delegate.onComplete();}
 }
}
