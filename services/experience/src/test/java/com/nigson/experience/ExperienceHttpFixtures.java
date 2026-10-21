package com.nigson.experience;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Local signed HTTP fixture using synthetic OAuth and actor keys. */
public final class ExperienceHttpFixtures implements AutoCloseable {
    private final KeyPair rsa, ec;
    private final HttpServer jwks;
    private final String issuer;
    private final ConfigurableApplicationContext app;
    public ExperienceHttpFixtures(String jdbcUrl) throws Exception {
        var rg = KeyPairGenerator.getInstance("RSA"); rg.initialize(2048); rsa = rg.generateKeyPair();
        var eg = KeyPairGenerator.getInstance("EC"); eg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1")); ec = eg.generateKeyPair();
        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); issuer = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/realms/offline";
        jwks.createContext("/", x -> { byte[] body = EventCodec.JSON.writeValueAsBytes(Map.of("keys", List.of(new RSAKey.Builder((RSAPublicKey) rsa.getPublic()).keyID("synthetic").algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build().toJSONObject()))); x.getResponseHeaders().set("Content-Type", "application/json"); x.sendResponseHeaders(200, body.length); x.getResponseBody().write(body); x.close(); }); jwks.start();
        var props = new HashMap<String,Object>(); props.put("APP_ENV","test"); props.put("NIGSON_SECURITY_ENABLED","true"); props.put("NIGSON_PRODUCT_PHASE","4"); props.put("NIGSON_EVENTS_ENABLED","false"); props.put("NIGSON_JDBC_URL",jdbcUrl+"?currentSchema=experience"); props.put("NIGSON_DB_USER","nigson_experience_app"); props.put("NIGSON_DB_PASSWORD","mock-experience-runtime"); props.put("NIGSON_OIDC_ISSUER",issuer); props.put("NIGSON_OIDC_JWKS",issuer+"/protocol/openid-connect/certs"); props.put("NIGSON_PRIVATE_PEER_CIDRS","127.0.0.1/32"); props.put("NIGSON_ACTOR_PUBLIC_KEYS",EventCodec.JSON.writeValueAsString(Map.of("active",Base64.getEncoder().encodeToString(ec.getPublic().getEncoded()))));
        props.put("NIGSON_DISTRIBUTOR_REQUIRED_DOCUMENT_TYPES","CERTIFICATE,IDENTITY"); props.put("NIGSON_DISTRIBUTOR_RECIPIENT_GROUP","synthetic-distributor-review"); props.put("NIGSON_DISTRIBUTOR_COMMERCE_MODE","mock");
        byte[] ph=new byte[32],pc=new byte[32],ic=new byte[32];Arrays.fill(ph,(byte)7);Arrays.fill(pc,(byte)8);Arrays.fill(ic,(byte)9);props.put("NIGSON_MESSAGING_INBOUND_ENABLED","true");props.put("NIGSON_LEAD_RECIPIENT_GROUP","synthetic-sales-triage");props.put("NIGSON_PHONE_CIPHER_REVISION","synthetic");props.put("NIGSON_PHONE_HASH_KEY_VERSION","1");props.put("NIGSON_PHONE_LOCAL_HASH_KEY",Base64.getEncoder().encodeToString(ph));props.put("NIGSON_PHONE_LOCAL_CIPHER_KEY",Base64.getEncoder().encodeToString(pc));props.put("NIGSON_INBOUND_LOCAL_CIPHER_KEY",Base64.getEncoder().encodeToString(ic));props.put("NIGSON_INBOUND_RETENTION_DAYS","30");props.put("NIGSON_TWILIO_ACCOUNT_SID","AC0123456789abcdef0123456789abcdef");props.put("NIGSON_TWILIO_AUTH_TOKEN","synthetic-auth-token-that-is-long-enough-never-live");props.put("NIGSON_TWILIO_WHATSAPP_FROM","whatsapp:+2348031234567");props.put("NIGSON_TWILIO_WHATSAPP_PUBLIC_URL","https://callbacks.synthetic.invalid/api/v1/webhooks/notifications/twilio/whatsapp");props.put("server.port","0");props.put("logging.level.root","WARN");
        app = new SpringApplicationBuilder(ExperienceApplication.class).properties(props).run();
    }
    public ConfigurableApplicationContext app() { return app; }
    public String base() { return "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort(); }
    public String workload(String scope, String client) throws Exception { var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("synthetic").build(),new JWTClaimsSet.Builder().issuer(issuer).subject("synthetic-workload").audience("nigson-experience").claim("typ","Bearer").claim("client_id",client).claim("azp",client).claim("scope",scope).issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(120))).build());jwt.sign(new RSASSASigner((RSAPrivateKey)rsa.getPrivate()));return jwt.serialize(); }
    public String actor(UUID party, String role, String scope, String method, String path, String body, UUID organization) throws Exception {
        var a=new ActorAssertion.Actor(party,issuer,"synthetic-"+party,null==organization?null:organization,Set.of(role),Set.of(scope),role.equals("ADMIN")?Instant.now():null);
        var binding=new ActorAssertion.Binding("experience",scope,method,path,UUID.fromString("10000000-0000-4000-8000-000000000139"),ActorAssertion.Binding.bodyHash(body.getBytes(StandardCharsets.UTF_8)));
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("active").type(new JOSEObjectType("nigson-actor+jwt")).build(),ActorAssertion.claims(a,binding,Instant.now()));jwt.sign(new ECDSASigner((ECPrivateKey)ec.getPrivate()));String token=jwt.serialize();
        if(organization!=null)ActorAssertion.verify(token,binding,Map.of("active",(java.security.interfaces.ECPublicKey)ec.getPublic()),Set.of(issuer),Clock.systemUTC());
        return token;
    }
    public ActorAssertion.Actor verifyActor(String token,String scope,String method,String path,String body) {
        var binding=new ActorAssertion.Binding("experience",scope,method,path,UUID.fromString("10000000-0000-4000-8000-000000000139"),ActorAssertion.Binding.bodyHash(body.getBytes(StandardCharsets.UTF_8)));
        return ActorAssertion.verify(token,binding,Map.of("active",(java.security.interfaces.ECPublicKey)ec.getPublic()),Set.of(issuer),Clock.systemUTC());
    }
    public HttpResponse<String> put(String path,String workload,String actor,String match,String key,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(10)).header("X-Correlation-ID","10000000-0000-4000-8000-000000000139").header("Content-Type","application/json").header("If-Match",match).header("Idempotency-Key",key).header("Authorization","Bearer "+workload).header("X-Nigson-Actor",actor).PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    }
    @Override public void close() { app.close(); jwks.stop(0); }
}
