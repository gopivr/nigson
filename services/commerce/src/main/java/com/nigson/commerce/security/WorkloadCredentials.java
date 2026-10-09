package com.nigson.commerce.security;

import com.nigson.shared.security.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import org.springframework.security.oauth2.jwt.*;

/** Confidential service credentials stay separate from BFF/actor identity and browser tokens. */
public final class WorkloadCredentials {
    private final String issuer,client,secret;private final HttpClient http;private final Clock clock;private final Map<String,JwtDecoder> decoders;private Jwt current;
    public WorkloadCredentials(String env,String issuer,String client,String secret,int phase,SSLContext tls,Clock clock) {
        if(!Set.of("nigson-commerce","nigson-experience","nigson-integration","nigson-ai").contains(client)||client.equals("nigson-ai")&&phase<5||phase<1||phase>5||secret==null||secret.length()<32||secret.length()>512||!Set.of("local","test").contains(env)&&(secret.startsWith("mock-")||secret.startsWith("dummy-")))throw new IllegalArgumentException("WORKLOAD_CREDENTIAL_POLICY_REQUIRED");
        // Validate issuer even when this client has no active operations in this phase.
        new KeycloakIdentity.Policy(env,issuer,issuer+"/protocol/openid-connect/certs",client,Set.of(client),Set.of("validation-only"));
        this.issuer=issuer;this.client=client;this.secret=secret;this.clock=clock;var b=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER);if(tls!=null)b.sslContext(tls);http=b.build();
        var d=new HashMap<String,JwtDecoder>();PrivateWorkload.ROUTES.stream().filter(r->r.phase()<=phase&&r.client().equals(client)).forEach(r->d.put(r.api(),KeycloakIdentity.decoder(new KeycloakIdentity.Policy(env,issuer,issuer+"/protocol/openid-connect/certs","nigson-"+r.owner(),Set.of(client),Set.of(r.scope())),clock,tls)));decoders=Map.copyOf(d);
    }
    public synchronized String token(PrivateWorkload.Route route) {
        IdentityKeys.outsideTransaction();if(!route.client().equals(client)||!decoders.containsKey(route.api()))throw ActorAssertion.denied();
        if(current!=null&&current.getExpiresAt().isAfter(clock.instant().plusSeconds(30))){validate(current,route);return current.getTokenValue();}
        try {
            String form="grant_type=client_credentials&client_id="+URLEncoder.encode(client,StandardCharsets.UTF_8)+"&client_secret="+URLEncoder.encode(secret,StandardCharsets.UTF_8);
            var response=http.send(HttpRequest.newBuilder(URI.create(issuer+"/protocol/openid-connect/token")).timeout(Duration.ofSeconds(5)).header("Content-Type","application/x-www-form-urlencoded").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;try(var body=response.body()){bytes=body.readNBytes(65537);}
            if(response.statusCode()!=200||bytes.length>65536||!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw ActorAssertion.denied();
            var json=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build().readTree(bytes);if(!json.get("token_type").isString()||!json.get("token_type").asString().equals("Bearer")||!json.get("access_token").isString())throw ActorAssertion.denied();
            Jwt validated=decoders.get(route.api()).decode(json.get("access_token").asString());validate(validated,route);current=validated;return current.getTokenValue();
        }catch(Exception e){current=null;if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("WORKLOAD_IDENTITY_UNAVAILABLE");}
    }
    private void validate(Jwt jwt,PrivateWorkload.Route route){if(!jwt.getAudience().contains("nigson-"+route.owner())||!client.equals(jwt.getClaimAsString("azp"))||!new HashSet<>(List.of(jwt.getClaimAsString("scope").split(" "))).contains(route.scope())||!jwt.getExpiresAt().isAfter(clock.instant()))throw ActorAssertion.denied();}
}
