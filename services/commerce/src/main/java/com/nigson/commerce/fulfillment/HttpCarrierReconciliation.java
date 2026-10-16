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
/** Fixed signed command with bounded transport. An uncertain ACK always replays its original UUID. */
public final class HttpCarrierReconciliation implements CarrierReconciliationPort {
 public static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-196")&&r.client().equals("nigson-commerce")&&r.owner().equals("integration")).findFirst().orElseThrow();
 private final URI base;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;
 public HttpCarrierReconciliation(String env,String target,Function<PrivateWorkload.Route,String> credentials){base=com.nigson.commerce.security.IntegrationTarget.of(env,target);this.credentials=Objects.requireNonNull(credentials);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();}
 public static byte[] body(CarrierReconciliationValues.Request input){return EventCodec.JSON.writeValueAsBytes(input);}
 public CarrierReconciliationValues.Result reconcile(UUID shipment,CarrierReconciliationValues.Request input,UUID command,String assertion,UUID correlation){
  com.nigson.commerce.security.IdentityKeys.outsideTransaction();for(UUID id:Arrays.asList(shipment,command,correlation))ApiValues.uuid(id==null?null:id.toString());if(input==null||assertion==null||!assertion.matches("[A-Za-z0-9._~-]{1,16384}"))throw ApiValues.invalid();CompletableFuture<HttpResponse<byte[]>> future=null;
  try{String token=credentials.apply(ROUTE);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);var request=HttpRequest.newBuilder(base.resolve(ROUTE.path().replace("{id}",shipment.toString()))).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("X-Nigson-Actor",assertion).header("X-Correlation-ID",correlation.toString()).header("Idempotency-Key",command.toString()).header("Content-Type","application/json").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body(input))).build();future=http.sendAsync(request,info->new Limited());var response=future.get(6,TimeUnit.SECONDS);String mime=response.headers().allValues("Content-Type").size()==1?response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip():"";
   if(response.statusCode()!=202){if(mime.equals("application/problem+json")){var error=EventCodec.JSON.readTree(response.body());for(var safe:List.of(ProblemCode.VALIDATION_ERROR,ProblemCode.ACCESS_DENIED,ProblemCode.INVALID_TRANSITION,ProblemCode.IDEMPOTENCY_HASH_MISMATCH,ProblemCode.PROVIDER_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH,ProblemCode.FEATURE_DISABLED))if(safe.status==response.statusCode()&&safe.name().equals(error.path("code").asString()))throw new Problem.Failure(safe);}throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
   if(!mime.equals("application/json"))throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);try{return CarrierReconciliationValues.result(EventCodec.JSON.readTree(response.body()),shipment);}catch(RuntimeException invalid){throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
  }catch(Problem.Failure safe){throw safe;}catch(Exception uncertain){if(uncertain instanceof InterruptedException)Thread.currentThread().interrupt();throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}finally{if(future!=null&&!future.isDone())future.cancel(true);}
 }
 private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
  private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private java.util.concurrent.Flow.Subscription subscription;private int size;private boolean failed;
  public CompletionStage<byte[]> getBody(){return delegate.getBody();}public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}public void onNext(List<ByteBuffer> buffers){if(failed)return;for(var buffer:buffers){size+=buffer.remaining();if(size>65536){failed=true;subscription.cancel();delegate.onError(new IllegalStateException("CARRIER_COMMAND_RESPONSE_LIMIT"));return;}}delegate.onNext(buffers);}public void onError(Throwable failure){delegate.onError(failure);}public void onComplete(){delegate.onComplete();}
 }
}
