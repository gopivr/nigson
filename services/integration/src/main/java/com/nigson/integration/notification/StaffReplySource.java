package com.nigson.integration.notification;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.integration.provider.PrivateHttp;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.security.ActorAssertion;

/** Fixed owner-to-owner context and suppression reads; never exposes arbitrary target URLs. */
public final class StaffReplySource {
    public record Context(String phone,String channel,String destinationHash,boolean eligible,String basis,List<String> templates) {
        public Context { templates=List.copyOf(templates); }
        @Override public String toString(){return "StaffReplyContext[protected]";}
    }
    private static final PrivateWorkload.Route CONTEXT=route("API-194");
    private static final PrivateWorkload.Route SUPPRESSION=route("API-205");
    private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder(
        tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096).build()).build())
        .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final URI base;private final HttpClient http;private final Function<PrivateWorkload.Route,String> credentials;private final Clock clock;

    public StaffReplySource(String environment,String endpoint,Function<PrivateWorkload.Route,String> credentials,Clock clock){
        if(!Set.of("local","test","dev","qa","stage","prod").contains(environment)||endpoint==null||endpoint.length()>2048)throw new IllegalArgumentException("MESSAGE_SOURCE_CONFIGURATION_REQUIRED");
        var uri=URI.create(endpoint);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("PRIVATE_TARGET_REQUIRED");
        if(Set.of("local","test").contains(environment)){if(!"http".equals(uri.getScheme())||!Set.of("experience","127.0.0.1","localhost").contains(uri.getHost()))throw new IllegalArgumentException("LOCAL_PRIVATE_TARGET_REQUIRED");}
        else if(!"https".equals(uri.getScheme())||!uri.getHost().endsWith(".internal")||uri.getPort()!=-1)throw new IllegalArgumentException("APPROVED_PRIVATE_HTTPS_REQUIRED");
        base=uri;this.credentials=Objects.requireNonNull(credentials);this.clock=Objects.requireNonNull(clock);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Context read(UUID lead,UUID correlation,String actorAssertion){
        outside();if(lead==null||lead.equals(new UUID(0,0))||correlation==null||correlation.equals(new UUID(0,0))||actorAssertion==null||actorAssertion.length()>8192)throw ApiValues.invalid();
        String path=CONTEXT.path().replace("{id}",lead.toString());var response=request(CONTEXT,path,null,correlation,actorAssertion);
        if(response.statusCode()!=200)throw sourceFailure(response.statusCode());var body=json(response);
        if(!body.isObject()||!body.propertyNames().equals(Set.of("phone","channel","destinationHash","eligible","basis","approvedTemplateCodes"))||!body.path("phone").isString()||!body.path("phone").asString().matches("\\+234[1-9][0-9]{9}")||!"WHATSAPP".equals(body.path("channel").asString())||!body.path("destinationHash").isString()||!body.path("destinationHash").asString().matches("[a-f0-9]{64}")||!body.path("eligible").isBoolean()||!body.path("eligible").asBoolean()||!Set.of("SIGNED_INBOUND_REQUEST","VERIFIED_DESTINATION").contains(body.path("basis").asString())||!body.path("approvedTemplateCodes").isArray()||body.path("approvedTemplateCodes").size()>10)throw unavailable();
        var templates=new ArrayList<String>();for(var item:body.path("approvedTemplateCodes")){if(!item.isString()||!"STAFF_LEAD_REPLY".equals(item.asString())||templates.contains(item.asString()))throw unavailable();templates.add(item.asString());}
        return new Context(body.path("phone").asString(),"WHATSAPP",body.path("destinationHash").asString(),true,body.path("basis").asString(),templates);
    }

    public boolean suppressed(String hash,String channel,UUID correlation){
        outside();if(hash==null||!hash.matches("[a-f0-9]{64}")||!"WHATSAPP".equals(channel)||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        byte[] bytes=EventCodec.JSON.writeValueAsBytes(Map.of("destinationHash",hash,"channel",channel));var response=request(SUPPRESSION,SUPPRESSION.path(),bytes,correlation,null);
        if(response.statusCode()!=200)throw sourceFailure(response.statusCode());var body=json(response);if(!body.isObject()||!body.propertyNames().equals(Set.of("suppressed"))||!body.path("suppressed").isBoolean())throw unavailable();return body.path("suppressed").asBoolean();
    }

    private HttpResponse<byte[]> request(PrivateWorkload.Route route,String path,byte[] body,UUID correlation,String actor){
        outside();try{String token=credentials.apply(route);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw unavailable();var b=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Accept","application/json").header("X-Correlation-ID",correlation.toString());if(actor!=null)b.header("X-Nigson-Actor",actor);if(body!=null)b.header("Content-Type","application/json");var request=b.method(route.method(),body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build();return PrivateHttp.send(http,request,8192,Duration.ofSeconds(6));}catch(Problem.Failure safe){throw safe;}catch(RuntimeException failure){throw unavailable();}
    }
    private static tools.jackson.databind.JsonNode json(HttpResponse<byte[]> response){if(response.headers().allValues("Content-Type").size()!=1||!"application/json".equals(response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip()))throw unavailable();try{return JSON.readTree(response.body());}catch(RuntimeException invalid){throw unavailable();}}
    private static PrivateWorkload.Route route(String api){return PrivateWorkload.ROUTES.stream().filter(r->r.api().equals(api)&&r.owner().equals("experience")&&r.client().equals("nigson-integration")).findFirst().orElseThrow(ActorAssertion::denied);}
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("MESSAGE_SOURCE_HTTP_OUTSIDE_TRANSACTION_REQUIRED");}
    private static Problem.Failure sourceFailure(int status){return new Problem.Failure(switch(status){case 401,403->ProblemCode.ACCESS_DENIED;case 404->ProblemCode.NOT_FOUND;case 409,422->ProblemCode.NOTIFICATION_RULE_VIOLATION;default->ProblemCode.PROVIDER_UNAVAILABLE;});}
    private static Problem.Failure unavailable(){return new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
}
