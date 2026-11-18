package com.nigson.integration.carrier;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import com.nigson.integration.provider.PrivateHttp;
import com.nigson.shared.security.PrivateWorkload;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
/** Exact existing API-119/120 workload routes; no browser input or cross-owner SQL. */
public class PandagoCommerceClient {
 private final URI base;private final Function<PrivateWorkload.Route,String> credentials;private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 public PandagoCommerceClient(String environment,String endpoint,Function<PrivateWorkload.Route,String> credentials){base=URI.create(endpoint);if(base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!Set.of("","/").contains(base.getPath()))throw new IllegalArgumentException("PRIVATE_TARGET_REQUIRED");if(Set.of("local","test").contains(environment)){if(!"http".equals(base.getScheme())||!Set.of("commerce","localhost","127.0.0.1").contains(base.getHost()))throw new IllegalArgumentException("LOCAL_PRIVATE_TARGET_REQUIRED");}else if(!Set.of("dev","qa","stage","prod").contains(environment)||!"https".equals(base.getScheme())||!base.getHost().endsWith(".internal")||base.getPort()!=-1)throw new IllegalArgumentException("APPROVED_PRIVATE_HTTPS_REQUIRED");this.credentials=Objects.requireNonNull(credentials);}
 private JsonNode request(String api,UUID shipment,JsonNode body){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals(api)&&r.client().equals("nigson-integration")&&r.owner().equals("commerce")).findFirst().orElseThrow();String token=credentials.apply(route);if(token==null||!token.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);var builder=HttpRequest.newBuilder(base.resolve(route.path().replace("{id}",shipment.toString()))).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Accept","application/json").header("X-Correlation-ID",UUID.randomUUID().toString());if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(EventCodec.JSON.writeValueAsBytes(body)));var response=PrivateHttp.send(http,builder.build(),65536,Duration.ofSeconds(6));if(response.statusCode()!=200||response.headers().allValues("Content-Type").size()!=1||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equals("application/json"))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);return EventCodec.JSON.readTree(response.body());}
 public JsonNode context(UUID shipment){return request("API-119",shipment,null);}
 public void acknowledge(UUID shipment,JsonNode result){var ack=request("API-120",shipment,result);if(!shipment.toString().equals(ack.path("id").asString())||"BOOKED".equals(result.path("bookingState").asString())&&!result.path("reference").asString().equals(ack.path("providerOrderId").asString()))throw new Problem.Failure(ProblemCode.PROVIDER_RULE_VIOLATION);}
}
