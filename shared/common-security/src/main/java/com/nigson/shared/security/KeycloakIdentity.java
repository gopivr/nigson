package com.nigson.shared.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;

/** Keycloak wire trust only. No roles, owner SQL, BFF sessions or domain authorization. */
public final class KeycloakIdentity {
    private KeycloakIdentity() {}
    public record Policy(String environment,String issuer,String jwks,String audience,Set<String> clients,Set<String> scopes) {
        public Policy {
            clients=Set.copyOf(clients);scopes=Set.copyOf(scopes);
            if(!Set.of("local","test","dev","qa","stage","prod").contains(environment)||clients.isEmpty()||scopes.isEmpty()
               || !bounded(audience)||clients.stream().anyMatch(s->!bounded(s))||scopes.stream().anyMatch(s->!bounded(s))) throw new IllegalArgumentException("IDENTITY_POLICY_REQUIRED");
            URI origin=URI.create(issuer),keys=URI.create(jwks);
            boolean local=Set.of("local","test").contains(environment);
            if(!endpoint(origin,local)||!endpoint(keys,local)||!origin.getPath().matches("/realms/[a-zA-Z0-9-]{1,63}")
               ||origin.getPath().equals("/realms/master")||!keys.getPath().equals(origin.getPath()+"/protocol/openid-connect/certs"))throw new IllegalArgumentException("IDENTITY_TARGET_INVALID");
            if(!local && (Set.of("localhost","127.0.0.1","keycloak").contains(origin.getHost())||!origin.getAuthority().equals(keys.getAuthority())||origin.getHost().endsWith(".example")||origin.getHost().endsWith(".test")||origin.getHost().endsWith(".invalid")))throw new IllegalArgumentException("APPROVED_IDENTITY_HTTPS_REQUIRED");
        }
    }
    private static tools.jackson.databind.json.JsonMapper json(){
        var factory=tools.jackson.core.json.JsonFactory.builder().streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(16384).maxNumberLength(20).build()).enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        return tools.jackson.databind.json.JsonMapper.builder(factory).build();
    }
    private static boolean bounded(String value){return value!=null&&value.matches("[A-Za-z0-9:._-]{1,100}");}
    private static boolean endpoint(URI u,boolean local){return u.getHost()!=null&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null
        && ("https".equals(u.getScheme()) || local&&"http".equals(u.getScheme())&&Set.of("localhost","127.0.0.1","keycloak").contains(u.getHost()));}
    public static JwtDecoder decoder(Policy policy){return decoder(policy,Clock.systemUTC(),null);}
    public static JwtDecoder decoder(Policy policy,Clock clock,SSLContext tls){
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER);
        if(tls!=null)client.sslContext(tls);
        var factory=new JdkClientHttpRequestFactory(client.build());factory.setReadTimeout(Duration.ofSeconds(3));
        var transport=new RestTemplate(factory);var lastFetch=new AtomicLong(Long.MIN_VALUE);
        transport.getInterceptors().add((request,body,execution)->{
            if(!request.getURI().toString().equals(policy.jwks()))throw new java.io.IOException("KEY_TARGET_DENIED");
            long now=System.nanoTime();long previous=lastFetch.get();
            if(previous!=Long.MIN_VALUE&&now-previous<Duration.ofSeconds(5).toNanos()||!lastFetch.compareAndSet(previous,now))throw new java.io.IOException("KEY_REFRESH_BOUNDED");
            ClientHttpResponse response=execution.execute(request,body);
            byte[] bytes;try(response){bytes=response.getBody().readNBytes(65537);if(bytes.length>65536)throw new java.io.IOException("KEY_RESPONSE_BOUND");if(!response.getStatusCode().is2xxSuccessful())throw new java.io.IOException("KEY_HTTP_STATUS_"+response.getStatusCode().value());
                try {
                    var tree=json().readTree(bytes);var keys=tree.get("keys");
                    if(keys==null||!keys.isArray()||keys.size()<1||keys.size()>10)throw new IllegalArgumentException();
                    Set<String> seen=new HashSet<>();
                    for(var key:keys){
                        if(!key.isObject()||!key.has("kid")||!key.get("kid").isString()||!bounded(key.get("kid").asText())||!seen.add(key.get("kid").asText()))throw new IllegalArgumentException();
                        for(String secret:List.of("d","p","q","dp","dq","qi","k"))if(key.has(secret))throw new IllegalArgumentException();
                    }
                }catch(RuntimeException rejected){throw new java.io.IOException("KEY_SET_REJECTED");}
                // Only bounded public key JSON is returned. Encryption keys cannot verify RS256/ES256 signatures.
                var status=response.getStatusCode();var headers=response.getHeaders();
                return new ClientHttpResponse(){public org.springframework.http.HttpStatusCode getStatusCode(){return status;}public String getStatusText(){return "";}public void close(){}public org.springframework.http.HttpHeaders getHeaders(){return headers;}public java.io.InputStream getBody(){return new java.io.ByteArrayInputStream(bytes);}};
            }
        });
        var signed=NimbusJwtDecoder.withJwkSetUri(policy.jwks()).restOperations(transport).cache(new KeyCache(clock))
            .jwsAlgorithms(algorithms->{algorithms.clear();algorithms.add(SignatureAlgorithm.RS256);algorithms.add(SignatureAlgorithm.ES256);}).build();
        var time=new JwtTimestampValidator(Duration.ofSeconds(60));time.setClock(clock);
        signed.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(policy.issuer()),jwt->validate(jwt,policy,clock)));
        return token->{
            if(token==null||token.length()>16384||!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))throw new BadJwtException("IDENTITY_TOKEN_REJECTED");
            try{
                var parts=token.split("\\.");
                var header=json().readTree(Base64.getUrlDecoder().decode(parts[0]));
                var claims=json().readTree(Base64.getUrlDecoder().decode(parts[1]));
                if(!header.isObject()||header.size()>20||!claims.isObject()||claims.size()>100||header.has("crit")||header.has("b64"))throw new IllegalArgumentException();
                for(String name:List.of("iss","sub","azp","typ","scope"))if(!claims.has(name)||!claims.get(name).isString())throw new IllegalArgumentException();
                if(claims.has("client_id")&&!claims.get("client_id").isString())throw new IllegalArgumentException();
                var audiences=claims.get("aud");
                if(audiences==null||!audiences.isString()&&(!audiences.isArray()||audiences.size()<1||audiences.size()>10))throw new IllegalArgumentException();
                if(audiences.isArray())for(var audience:audiences)if(!audience.isString())throw new IllegalArgumentException();
                for(String date:List.of("iat","exp"))if(!claims.has(date)||!claims.get(date).isIntegralNumber())throw new IllegalArgumentException();
                if(claims.has("nbf")&&!claims.get("nbf").isIntegralNumber())throw new IllegalArgumentException();
                return signed.decode(token);
            }catch(RuntimeException failure){throw new BadJwtException("IDENTITY_TOKEN_REJECTED");}
        };
    }
    private static OAuth2TokenValidatorResult validate(Jwt jwt,Policy p,Clock clock){
        try{
            String azp=jwt.getClaimAsString("azp"),client=jwt.getClaimAsString("client_id"),kind=jwt.getClaimAsString("typ"),scope=jwt.getClaimAsString("scope");
            boolean valid="Bearer".equals(kind)&&jwt.getHeaders().get("kid") instanceof String kid&&bounded(kid)
                &&jwt.getExpiresAt()!=null&&jwt.getIssuedAt()!=null&&!jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(60))
                &&jwt.getExpiresAt().isAfter(jwt.getIssuedAt())&&jwt.getSubject()!=null&&!jwt.getSubject().isEmpty()&&jwt.getSubject().length()<=200
                &&p.clients().contains(azp)&&(client==null||client.equals(azp))&&jwt.getAudience().contains(p.audience())
                &&scope!=null&&scope.length()<=4096&&Arrays.stream(scope.split(" ")).allMatch(KeycloakIdentity::bounded)
                &&new HashSet<>(Arrays.asList(scope.split(" "))).containsAll(p.scopes());
            if(valid)return OAuth2TokenValidatorResult.success();
        }catch(RuntimeException rejected){/* Malformed claims are denied without claims/exception text. */}
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","IDENTITY_TOKEN_REJECTED",null));
    }
    /** One public-key set per decoder; a hard five-minute deadline, never stale-on-error. */
    private static final class KeyCache implements Cache {
        private final Clock clock;private Object key,value;private Instant expires=Instant.MIN;
        KeyCache(Clock clock){this.clock=clock;}
        public String getName(){return "keycloak-jwks";}public Object getNativeCache(){return this;}
        public synchronized ValueWrapper get(Object k){return Objects.equals(k,key)&&clock.instant().isBefore(expires)&&value!=null?new SimpleValueWrapper(value):null;}
        public <T>T get(Object k,Class<T> type){var found=get(k);return found==null?null:type.cast(found.get());}
        public <T>T get(Object k,Callable<T> loader){var found=get(k);if(found!=null){@SuppressWarnings("unchecked")T result=(T)found.get();return result;}try{T result=loader.call();put(k,result);return result;}catch(Exception failure){throw new ValueRetrievalException(k,loader,new IllegalStateException("KEY_LOAD_FAILED"));}}
        public synchronized void put(Object k,Object v){key=k;value=v;expires=clock.instant().plusSeconds(300);}
        public synchronized ValueWrapper putIfAbsent(Object k,Object v){var old=get(k);if(old==null)put(k,v);return old;}
        public synchronized void evict(Object k){if(Objects.equals(k,key))clear();}public synchronized void clear(){key=null;value=null;expires=Instant.MIN;}
    }
}
