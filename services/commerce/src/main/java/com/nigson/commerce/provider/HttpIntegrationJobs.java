package com.nigson.commerce.provider;

import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Function;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Fixed compiled private routes; no redirects, retries or browser credential forwarding. */
public final class HttpIntegrationJobs implements IntegrationJobs {
    private final URI base;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;
    private static final PrivateWorkload.Route READ=route("API-140"),RETRY=route("API-141");
    private static PrivateWorkload.Route route(String api){return PrivateWorkload.ROUTES.stream().filter(r->r.api().equals(api)&&r.owner().equals("integration")&&r.client().equals("nigson-commerce")).findFirst().orElseThrow();}
    public HttpIntegrationJobs(String env,String target,Function<PrivateWorkload.Route,String> credentials){
        base=target(env,target);this.credentials=Objects.requireNonNull(credentials);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public static URI target(String env,String input){return com.nigson.commerce.security.IntegrationTarget.of(env,input);}
    public boolean configured(){return true;}
    public JobValues.Page list(JobValues.Query q,UUID correlation){
        String query="?page="+q.page()+"&size="+q.size()+"&sort="+URLEncoder.encode(q.sort(),StandardCharsets.UTF_8)+(q.state()==null?"":"&state="+q.state());
        var response=send(READ,"/internal/v1/jobs"+query,null,null,null,null,correlation);
        try{return JobValues.page(EventCodec.JSON.readTree(response.body()),q);}catch(RuntimeException invalid){throw failure(false);}
    }
    public Accepted retry(UUID job,long version,JobValues.Retry body,String commandKey,String actor,UUID correlation){
        if(job==null||job.equals(new UUID(0,0))||version<0||actor==null||actor.isBlank()||actor.length()>16384)throw ApiValues.invalid();ApiValues.idempotencyKey(commandKey);
        byte[] bytes=EventCodec.JSON.writeValueAsBytes(body);var response=send(RETRY,"/internal/v1/jobs/"+job+"/retry",bytes,version,commandKey,actor,correlation);
        try{var dto=JobValues.job(EventCodec.JSON.readTree(response.body()));long acceptedVersion=ApiValues.version(single(response,"ETag"));if(!dto.id().equals(job)||acceptedVersion<=version)throw ApiValues.invalid();return new Accepted(dto,acceptedVersion);}catch(RuntimeException invalid){throw failure(true);}
    }
    private HttpResponse<byte[]> send(PrivateWorkload.Route route,String path,byte[] body,Long version,String key,String actor,UUID correlation){
        com.nigson.commerce.security.IdentityKeys.outsideTransaction();if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        String token;try{token=credentials.apply(route);}catch(RuntimeException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
        if(token==null||token.isBlank()||token.length()>16384)throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
        var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Accept","application/json").header("X-Correlation-ID",correlation.toString());
        var trace=com.nigson.shared.observability.WireContext.traceparent();if(trace!=null)request.header("traceparent",trace);
        if(body==null)request.GET();else request.header("Content-Type","application/json").header("If-Match","\""+version+"\"").header("Idempotency-Key",key).header("X-Nigson-Actor",actor).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try{
            future=http.sendAsync(request.build(),info->new LimitedBody());var response=future.get(6,TimeUnit.SECONDS);
            if(response.statusCode()!=(body==null?200:202)){
                // Only compiled problem codes cross the owner boundary; never upstream messages.
                if(Set.of(400,403,404,409,412,422).contains(response.statusCode())&&single(response,"Content-Type").startsWith("application/problem+json")){
                    var json=EventCodec.JSON.readTree(response.body());String code=json.path("code").asString();
                    for(var safe:List.of(ProblemCode.VALIDATION_ERROR,ProblemCode.ACCESS_DENIED,ProblemCode.NOT_FOUND,ProblemCode.VERSION_MISMATCH,ProblemCode.INVALID_TRANSITION,ProblemCode.PROVIDER_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH,ProblemCode.FEATURE_DISABLED))if(safe.name().equals(code)&&safe.status==response.statusCode())throw new Problem.Failure(safe);
                }
                throw failure(body!=null);
            }
            if(!single(response,"Content-Type").split(";",2)[0].strip().equals("application/json"))throw failure(body!=null);return response;
        }catch(Problem.Failure safe){throw safe;}catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();throw failure(body!=null);}finally{if(future!=null&&!future.isDone())future.cancel(true);}
    }
    private static String single(HttpResponse<?> response,String name){var values=response.headers().allValues(name);if(values.size()!=1)throw new IllegalStateException("UPSTREAM_HEADER_INVALID");return values.getFirst();}
    private static Problem.Failure failure(boolean mutation){return new Problem.Failure(mutation?ProblemCode.PROVIDER_OUTCOME_UNKNOWN:ProblemCode.PROVIDER_UNAVAILABLE);}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private Flow.Subscription subscription;private int bytes;private boolean failed;
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
        public void onNext(List<ByteBuffer> items){if(failed)return;for(var item:items){bytes+=item.remaining();if(bytes>65536){failed=true;subscription.cancel();delegate.onError(new IllegalStateException("UPSTREAM_RESPONSE_LIMIT"));return;}}delegate.onNext(items);}
        public void onError(Throwable failure){if(!failed)delegate.onError(failure);}
        public void onComplete(){if(!failed)delegate.onComplete();}
    }
}
