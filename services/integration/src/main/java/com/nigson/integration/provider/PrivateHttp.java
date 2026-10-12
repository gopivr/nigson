package com.nigson.integration.provider;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Bounded private wire transport; callers supply and validate fixed targets and safe semantics. */
final class PrivateHttp {
 private PrivateHttp(){}
 static HttpResponse<byte[]> send(HttpClient client,HttpRequest request,int limit,Duration deadline){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");if(limit<1||limit>65536||deadline.toMillis()<1||deadline.toMillis()>10000)throw new IllegalArgumentException("HTTP_BOUND_INVALID");
  var future=client.sendAsync(request,info->new Body(limit));try{return future.get(deadline.toMillis(),TimeUnit.MILLISECONDS);}catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("PRIVATE_HTTP_UNAVAILABLE");}finally{if(!future.isDone())future.cancel(true);}
 }
 private static final class Body implements HttpResponse.BodySubscriber<byte[]> {
  private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private final int limit;private int bytes;private Flow.Subscription subscription;
  Body(int limit){this.limit=limit;}public CompletionStage<byte[]> getBody(){return delegate.getBody();}public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}public void onNext(List<ByteBuffer> data){for(var part:data){bytes+=part.remaining();if(bytes>limit){subscription.cancel();delegate.onError(new IllegalStateException("PRIVATE_HTTP_BODY_TOO_LARGE"));return;}}delegate.onNext(data);}public void onError(Throwable failure){delegate.onError(failure);}public void onComplete(){delegate.onComplete();}
 }
}
