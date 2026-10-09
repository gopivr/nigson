package com.nigson.commerce.security;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.net.ssl.SSLContext;
import org.springframework.security.oauth2.jwt.*;
import com.nigson.shared.security.*;

/** Real confidential Keycloak HTTP adapter. No provider calls inside owner transactions. */
public final class OidcTrust {
    public record Tokens(String access,String refresh,Instant expires,String issuer,String subject,Instant mfaAt) {
        @Override public String toString(){return "Tokens[protected]";}
    }
    private final String issuer,client,secret,callback;private final HttpClient http;private final JwtDecoder access,id;private final Clock clock;
    public OidcTrust(String environment,String issuer,String client,String secret,String callback,SSLContext tls,Clock clock) {
        var c=URI.create(callback);var policy=new KeycloakIdentity.Policy(environment,issuer,issuer+"/protocol/openid-connect/certs","nigson-commerce",Set.of(client),Set.of("openid"));
        if(secret==null||secret.length()<32||secret.length()>512||!"nigson-bff".equals(client)||c.getHost()==null||c.getUserInfo()!=null||c.getQuery()!=null||c.getFragment()!=null||!c.getPath().equals("/bff/callback")||!"https".equals(c.getScheme())&&!(environment.equals("local")&&"http".equals(c.getScheme())&&Set.of("localhost","127.0.0.1").contains(c.getHost()))||!Set.of("local","test").contains(environment)&&(c.getHost().endsWith(".test")||c.getHost().endsWith(".example")||c.getHost().equals("localhost")||secret.startsWith("mock-")||secret.startsWith("dummy-")))throw new IllegalArgumentException("BFF_IDENTITY_CONFIG_REQUIRED");
        this.issuer=issuer;this.client=client;this.secret=secret;this.callback=callback;this.clock=clock;
        var b=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER);if(tls!=null)b.sslContext(tls);http=b.build();
        access=KeycloakIdentity.decoder(policy,clock,tls);id=KeycloakIdentity.idTokenDecoder(new KeycloakIdentity.Policy(environment,issuer,policy.jwks(),client,Set.of(client),Set.of("openid")),clock,tls);
    }
    public URI authorization(PendingLogin flow,boolean stepUp) {
        try{var q=new LinkedHashMap<String,String>();q.put("client_id",client);q.put("redirect_uri",callback);q.put("response_type","code");q.put("scope","openid");q.put("state",flow.state());q.put("nonce",flow.nonce());q.put("code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(flow.verifier().getBytes(StandardCharsets.US_ASCII))));q.put("code_challenge_method","S256");if(stepUp){q.put("max_age","0");q.put("prompt","login");}return URI.create(issuer+"/protocol/openid-connect/auth?"+form(q));}catch(Exception e){throw rejected();}
    }
    public Tokens exchange(String code,PendingLogin flow) {
        if(code==null||code.length()>2048||code.isBlank()||!clock.instant().isBefore(flow.expiresAt()))throw rejected();
        var data=request("token",Map.of("grant_type","authorization_code","code",code,"redirect_uri",callback,"code_verifier",flow.verifier()));
        Tokens tokens=validate(data,flow.nonce(),null);return tokens;
    }
    public Tokens refresh(SessionRecord record) {
        var data=request("token",Map.of("grant_type","refresh_token","refresh_token",record.refreshToken()));return validate(data,null,record);
    }
    /** Local invalidation is unconditional. Provider logout failure is surfaced, never fabricated as success. */
    public void revoke(String refreshToken){if(refreshToken==null||refreshToken.isBlank()||refreshToken.length()>16384)throw rejected();request("logout",Map.of("refresh_token",refreshToken));}
    private Tokens validate(tools.jackson.databind.JsonNode data,String nonce,SessionRecord previous) {
        String stage="RESPONSE";
        try {
            for(String name:List.of("access_token","refresh_token","id_token","token_type"))if(!data.has(name)||!data.get(name).isString())throw rejected();
            if(!"Bearer".equals(data.get("token_type").asString()))throw rejected();String a=data.get("access_token").asString(),r=data.get("refresh_token").asString();if(r.isBlank()||r.length()>16384)throw rejected();
            stage="ACCESS_SIGNATURE";Jwt accessJwt=access.decode(a);stage="ID_SIGNATURE";Jwt idJwt=id.decode(data.get("id_token").asString());stage="BINDING";
            if(!accessJwt.getSubject().equals(idJwt.getSubject())||!accessJwt.getIssuer().equals(idJwt.getIssuer())||nonce!=null&&!MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),idJwt.getClaimAsString("nonce").getBytes(StandardCharsets.UTF_8)))throw rejected();
            String hash=idJwt.getClaimAsString("at_hash");if(hash!=null){String expected=Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(a.getBytes(StandardCharsets.US_ASCII)),16));if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII)))throw rejected();}
            if(previous!=null&&(!previous.subject().equals(accessJwt.getSubject())||!previous.issuer().equals(accessJwt.getIssuer().toString())))throw rejected();
            Instant mfa=null;Object amr=idJwt.getClaims().get("amr"),time=idJwt.getClaims().get("auth_time");
            if(amr instanceof List<?> methods&&methods.stream().allMatch(v->v instanceof String)&&methods.contains("otp")&&time instanceof Number number){Instant t=Instant.ofEpochSecond(number.longValue());if(t.isAfter(clock.instant().plusSeconds(5))||!t.isAfter(clock.instant().minusSeconds(900)))throw rejected();mfa=t;}
            // Refresh cannot create a new MFA event or extend the original absolute lifetime.
            if(previous!=null)mfa=previous.mfaAt();
            return new Tokens(a,r,accessJwt.getExpiresAt(),accessJwt.getIssuer().toString(),accessJwt.getSubject(),mfa);
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger(OidcTrust.class).warn("OIDC_TRUST_DENIED category={}",stage);throw rejected();}
    }
    private tools.jackson.databind.JsonNode request(String operation,Map<String,String> values) {
        IdentityKeys.outsideTransaction();
        try {
            var fields=new TreeMap<>(values);fields.put("client_id",client);fields.put("client_secret",secret);
            var req=HttpRequest.newBuilder(URI.create(issuer+"/protocol/openid-connect/"+operation)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/x-www-form-urlencoded").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
            var response=http.send(req,HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;try(var stream=response.body()){bytes=stream.readNBytes(65537);}
            if(bytes.length>65536||response.statusCode()!=200&&response.statusCode()!=204)throw rejected();if(response.statusCode()==204&&operation.equals("logout"))return new tools.jackson.databind.json.JsonMapper().createObjectNode();
            if(!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw rejected();
            return tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(20000).maxNumberLength(20).build()).build()).build().readTree(bytes);
        }catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw rejected();}
    }
    private static String form(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    private static IllegalStateException rejected(){return new IllegalStateException("OIDC_TRUST_REJECTED");}
}
