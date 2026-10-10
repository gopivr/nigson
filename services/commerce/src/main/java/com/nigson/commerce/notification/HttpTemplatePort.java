package com.nigson.commerce.notification;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import com.nigson.commerce.security.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.PrivateWorkload;

/** Actual fixed API-142 HTTP adapter, with no provider or browser success fallback. */
public final class HttpTemplatePort implements TemplatePort {
    private final URI base;private final HttpClient http;private final java.util.function.Function<PrivateWorkload.Route,String> credentials;
    private static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-142")&&r.owner().equals("integration")&&r.client().equals("nigson-commerce")&&r.scope().equals("templates.write_WITH_SIGNED_ADMIN")).findFirst().orElseThrow();
    public HttpTemplatePort(String env,String target,java.util.function.Function<PrivateWorkload.Route,String> credentials){base=IntegrationTarget.of(env,target);this.credentials=Objects.requireNonNull(credentials);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();}
    public boolean configured(){return true;}
    public Accepted put(TemplateValues.Template template,Long version,String command,String actor,UUID correlation){
        IdentityKeys.outsideTransaction();ApiValues.idempotencyKey(command);if(actor==null||actor.isBlank()||actor.length()>16384||correlation==null||correlation.equals(new UUID(0,0))||version!=null&&version<0)throw ApiValues.invalid();
        String token;try{token=credentials.apply(ROUTE);}catch(RuntimeException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
        var request=HttpRequest.newBuilder(base.resolve("/internal/v1/templates")).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("Idempotency-Key",command).header("X-Nigson-Actor",actor).header("X-Correlation-ID",correlation.toString());
        if(version!=null)request.header("If-Match","\""+version+"\"");String trace=com.nigson.shared.observability.WireContext.traceparent();if(trace!=null)request.header("traceparent",trace);
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try{byte[] payload=EventCodec.JSON.writeValueAsBytes(TemplateValues.wire(template));future=http.sendAsync(request.POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build(),info->new Limited());var response=future.get(6,TimeUnit.SECONDS);
            String type=single(response,"Content-Type");var input=EventCodec.JSON.readTree(response.body());
            if(response.statusCode()!=201){
                if(type.split(";",2)[0].strip().equals("application/problem+json"))for(var safe:List.of(ProblemCode.VALIDATION_ERROR,ProblemCode.ACCESS_DENIED,ProblemCode.NOT_FOUND,ProblemCode.CONFLICT,ProblemCode.VERSION_MISMATCH,ProblemCode.INVALID_TRANSITION,ProblemCode.NOTIFICATION_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH,ProblemCode.FEATURE_DISABLED))if(safe.status==response.statusCode()&&safe.name().equals(input.path("code").asString()))throw new Problem.Failure(safe);
                throw unknown();
            }
            if(!type.split(";",2)[0].strip().equals("application/json")||!input.isObject()||input.size()!=4||!Set.of("id","code","revision","active").containsAll(input.propertyNames())||!input.get("id").isString()||!input.get("code").isString()||!input.get("code").asString().equals(template.code())||!input.get("revision").isIntegralNumber()||!input.get("revision").canConvertToInt()||input.get("revision").asInt()!=template.revision()||!input.get("active").isBoolean()||input.get("active").asBoolean()!=template.active())throw unknown();
            var tags=response.headers().allValues("ETag");if(tags.size()>1||version!=null&&tags.isEmpty())throw unknown();try{Long acceptedVersion=tags.isEmpty()?null:ApiValues.version(tags.getFirst());return new Accepted(ApiValues.uuid(input.get("id").asString()),template.code(),template.revision(),template.active(),acceptedVersion);}catch(RuntimeException invalid){throw unknown();}
        }catch(Problem.Failure failure){throw failure;}catch(Exception uncertain){if(uncertain instanceof InterruptedException)Thread.currentThread().interrupt();throw unknown();}finally{if(future!=null&&!future.isDone())future.cancel(true);}
    }
    private static String single(HttpResponse<?> response,String header){var values=response.headers().allValues(header);if(values.size()!=1)throw new IllegalStateException("UPSTREAM_HEADER_INVALID");return values.getFirst();}
    private static Problem.Failure unknown(){return new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
    private static final class Limited implements HttpResponse.BodySubscriber<byte[]>{
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private Flow.Subscription subscription;private int bytes;private boolean failed;
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}public void onNext(List<ByteBuffer> values){if(failed)return;for(var v:values){bytes+=v.remaining();if(bytes>65536){failed=true;subscription.cancel();delegate.onError(new IllegalStateException("UPSTREAM_BODY_LIMIT"));return;}}delegate.onNext(values);}public void onError(Throwable t){if(!failed)delegate.onError(t);}public void onComplete(){if(!failed)delegate.onComplete();}
    }
}
