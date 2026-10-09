package com.nigson.commerce.bootstrap;
import com.nigson.shared.security.KeycloakIdentity;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class KeycloakTrustTest {
    HttpServer server; RSAKey rsa; ECKey ec; String issuer; AtomicInteger requests=new AtomicInteger();
    boolean unavailable; Clock clock=Clock.systemUTC();
    @BeforeEach void setup() throws Exception {
        rsa=new RSAKeyGenerator(2048).keyID("rsa-qualification").algorithm(JWSAlgorithm.RS256).generate();
        ec=new ECKeyGenerator(Curve.P_256).keyID("ec-qualification").algorithm(JWSAlgorithm.ES256).generate();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        issuer="http://127.0.0.1:"+server.getAddress().getPort()+"/realms/qualification";
        server.createContext("/realms/qualification/protocol/openid-connect/certs",exchange->{
            requests.incrementAndGet();byte[] body=new JWKSet(List.of(rsa.toPublicJWK(),ec.toPublicJWK())).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(unavailable?503:200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    KeycloakIdentity.Policy policy(String audience,Set<String> clients,Set<String> scopes){return new KeycloakIdentity.Policy("test",issuer,issuer+"/protocol/openid-connect/certs",audience,clients,scopes);}
    JwtDecoder decoder(){return KeycloakIdentity.decoder(policy("nigson-commerce",Set.of("nigson-integration"),Set.of("payments.verify")),clock,null);}
    Map<String,Object> claims(){var values=new HashMap<String,Object>();values.put("iss",issuer);values.put("aud",List.of("nigson-commerce"));values.put("sub","isolated-qualification");values.put("azp","nigson-integration");values.put("typ","Bearer");values.put("scope","payments.verify");values.put("iat",clock.instant().getEpochSecond());values.put("exp",clock.instant().plusSeconds(600).getEpochSecond());return values;}
    String sign(Map<String,Object> values,boolean elliptic) throws Exception{
        var header=new JWSHeader.Builder(elliptic?JWSAlgorithm.ES256:JWSAlgorithm.RS256).keyID(elliptic?ec.getKeyID():rsa.getKeyID()).build();
        var signed=new SignedJWT(header,JWTClaimsSet.parse(values));signed.sign(elliptic?new ECDSASigner(ec):new RSASSASigner(rsa));return signed.serialize();
    }
    @Test void acceptsOnlySupportedSignedAccessClaims() throws Exception {
        var decoder=decoder();assertNotNull(decoder.decode(sign(claims(),false)));assertNotNull(decoder.decode(sign(claims(),true)));
        for(var entry:Map.<String,Object>of("iss",issuer+"-wrong","aud",List.of("nigson-experience"),"azp","nigson-bff","scope","orders.write","typ","ID","client_id","nigson-commerce","exp",clock.instant().minusSeconds(120).getEpochSecond(),"nbf",clock.instant().plusSeconds(120).getEpochSecond(),"iat",clock.instant().plusSeconds(120).getEpochSecond()).entrySet()){
            var bad=claims();bad.put(entry.getKey(),entry.getValue());var token=sign(bad,false);var failure=assertThrows(BadJwtException.class,()->decoder.decode(token),entry.getKey());assertEquals("IDENTITY_TOKEN_REJECTED",failure.getMessage());
        }
        for(String field:List.of("iss","aud","azp","scope","typ","exp","iat","sub")){var bad=claims();bad.remove(field);String token=sign(bad,false);assertThrows(BadJwtException.class,()->decoder.decode(token));}
        for(String token:List.of("invalid","a.b.c","a".repeat(16385),"eyJhbGciOiJub25lIn0.e30."))assertThrows(BadJwtException.class,()->decoder.decode(token));
        assertEquals(1,requests.get());
    }
    @Test void refreshAndExpiryFailClosed() throws Exception {
        var now=Instant.now();var instant=new AtomicReference<>(now);clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return instant.get();}};
        var decoder=decoder();String token=sign(claims(),false);decoder.decode(token);
        rsa=new RSAKeyGenerator(2048).keyID("rotated-qualification").algorithm(JWSAlgorithm.RS256).generate();String next=sign(claims(),false);
        assertThrows(BadJwtException.class,()->decoder.decode(next));assertEquals(1,requests.get());
        Thread.sleep(5100);assertNotNull(decoder.decode(next));assertEquals(2,requests.get());
        instant.set(now.plusSeconds(301));unavailable=true;
        assertThrows(BadJwtException.class,()->decoder.decode(next));
        Thread.sleep(5100);assertThrows(BadJwtException.class,()->decoder.decode(next));assertEquals(3,requests.get());
    }
    @Test void rejectsDuplicateClaimsEvenWithValidSignature() throws Exception {
        String json=JsonMapper.builder().build().writeValueAsString(claims());json=json.substring(0,json.length()-1)+",\"azp\":\"nigson-integration\"}";
        var object=new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsa.getKeyID()).build(),new Payload(json));object.sign(new RSASSASigner(rsa));
        assertThrows(BadJwtException.class,()->decoder().decode(object.serialize()));
    }
    @Test void refusesWrongRawClaimTypesAndExactSkewBoundary() throws Exception {
        Instant now=Instant.ofEpochSecond(1900000000);clock=Clock.fixed(now,ZoneOffset.UTC);var decoder=decoder();
        var allowed=claims();allowed.put("iat",now.plusSeconds(60).getEpochSecond());assertNotNull(decoder.decode(sign(allowed,false)));
        var future=claims();future.put("iat",now.plusSeconds(61).getEpochSecond());assertThrows(BadJwtException.class,()->decoder.decode(sign(future,false)));
        for(var invalid:Map.<String,Object>of("sub",123,"scope",List.of("payments.verify"),"azp",true,"aud",List.of("nigson-commerce",123),"exp","1900000600","client_id",123).entrySet()){
            var values=claims();values.put(invalid.getKey(),invalid.getValue());
            var object=new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsa.getKeyID()).build(),new Payload(JsonMapper.builder().build().writeValueAsString(values)));object.sign(new RSASSASigner(rsa));
            assertThrows(BadJwtException.class,()->decoder.decode(object.serialize()),invalid.getKey());
        }
    }
    @Test void refusesProductionLocalTargetsAndEmptyTrust(){
        assertThrows(IllegalArgumentException.class,()->new KeycloakIdentity.Policy("prod",issuer,issuer+"/protocol/openid-connect/certs","nigson-commerce",Set.of("nigson-integration"),Set.of("payments.verify")));
        assertThrows(IllegalArgumentException.class,()->policy("nigson-commerce",Set.of(),Set.of("payments.verify")));
    }
    @Test void actualKeycloakAccessAndIdentityTokens() throws Exception {
        String fixture=System.getenv("NIGSON_IDENTITY_FIXTURE");Assumptions.assumeTrue(fixture!=null,"Real Keycloak is executed by identity-check");
        var data=JsonMapper.builder().build().readTree(Files.readString(Path.of(fixture)));
        var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);
        try(var input=Files.newInputStream(Path.of(data.get("ca").asText()))){store.setCertificateEntry("isolated-ca",CertificateFactory.getInstance("X.509").generateCertificate(input));}
        var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);
        var tls=SSLContext.getInstance("TLS");tls.init(null,factory.getTrustManagers(),null);
        String realIssuer=data.get("issuer").asText(),jwks=data.get("jwks").asText();String token=data.get("retail").get("access_token").asText();
        var accepted=KeycloakIdentity.decoder(new KeycloakIdentity.Policy("test",realIssuer,jwks,"nigson-commerce",Set.of("nigson-bff"),Set.of("openid")),Clock.systemUTC(),tls);
        var workload=KeycloakIdentity.decoder(new KeycloakIdentity.Policy("test",realIssuer,jwks,"nigson-commerce",Set.of("nigson-integration"),Set.of("payment.reconcile")),Clock.systemUTC(),tls);
        assertNotNull(workload.decode(data.get("workload").get("access_token").asText()));
        assertNotNull(accepted.decode(token));assertThrows(BadJwtException.class,()->accepted.decode(data.get("retail").get("id_token").asText()));
        for(var denied:List.of(new KeycloakIdentity.Policy("test",realIssuer,jwks,"nigson-experience",Set.of("nigson-bff"),Set.of("openid")),new KeycloakIdentity.Policy("test",realIssuer,jwks,"nigson-commerce",Set.of("nigson-integration"),Set.of("openid")),new KeycloakIdentity.Policy("test",realIssuer,jwks,"nigson-commerce",Set.of("nigson-bff"),Set.of("payments.verify")))){
            assertThrows(BadJwtException.class,()->KeycloakIdentity.decoder(denied,Clock.systemUTC(),tls).decode(token));
        }
    }
    @Test void idProfileIsSeparateAndRefreshNonceIsOptionalButStrictlyTyped()throws Exception {
        var p=policy("nigson-bff",Set.of("nigson-bff"),Set.of("openid"));var decoder=KeycloakIdentity.idTokenDecoder(p,clock,null);var good=claims();good.put("azp","nigson-bff");good.put("aud",List.of("nigson-bff"));good.put("typ","ID");good.remove("scope");good.put("nonce","server-one-use-nonce");assertNotNull(decoder.decode(sign(good,false)));good.remove("nonce");assertNotNull(decoder.decode(sign(good,false)));
        for(var wrong:Map.<String,Object>of("nonce",true,"typ","Bearer","azp","nigson-commerce","aud",List.of("nigson-commerce"),"auth_time","now","amr",List.of(42)).entrySet()){var bad=new HashMap<>(good);bad.put(wrong.getKey(),wrong.getValue());assertThrows(BadJwtException.class,()->decoder.decode(sign(bad,false)));}
    }

}
