package com.nigson.commerce.security;
import com.nigson.shared.security.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.*;
import java.time.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;

class SecurityProtocolTest {
 static final Instant NOW=Instant.parse("2026-10-09T03:00:00Z");static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
 static final String ISSUER="https://identity.synthetic.test/realms/offline";
 static KeyPair pair()throws Exception{var g=KeyPairGenerator.getInstance("EC");g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));return g.generateKeyPair();}
 static ActorAssertion.Actor actor(){return new ActorAssertion.Actor(UUID.randomUUID(),ISSUER,"subject",null,Set.of("CUSTOMER"),Set.of("order-status.read_WITH_SIGNED_ACTOR"),null);}
 static ActorAssertion.Binding binding(){return new ActorAssertion.Binding("commerce","order-status.read_WITH_SIGNED_ACTOR","GET","/internal/v1/orders/"+UUID.randomUUID()+"/status",UUID.randomUUID());}
 static String sign(KeyPair key,JWTClaimsSet c)throws Exception{var j=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("active").type(new JOSEObjectType("nigson-actor+jwt")).build(),c);j.sign(new ECDSASigner((ECPrivateKey)key.getPrivate()));return j.serialize();}
 @Test void actorBindingsExpiryAndSubjectAreMandatory()throws Exception{
  var key=pair();var a=actor();var b=binding();var c=ActorAssertion.claims(a,b,NOW);String token=sign(key,c);var keys=Map.of("active",(ECPublicKey)key.getPublic());assertThat(ActorAssertion.verify(token,b,keys,Set.of(ISSUER),CLOCK).party()).isEqualTo(a.party());
  for(var altered:List.of(new JWTClaimsSet.Builder(c).audience("integration").build(),new JWTClaimsSet.Builder(c).subject("not-a-party").build(),new JWTClaimsSet.Builder(c).claim("operation","jobs.read").build(),new JWTClaimsSet.Builder(c).claim("http_method","POST").build(),new JWTClaimsSet.Builder(c).claim("resource","/internal/v1/orders/"+UUID.randomUUID()+"/status").build(),new JWTClaimsSet.Builder(c).claim("correlation_id",UUID.randomUUID().toString()).build(),new JWTClaimsSet.Builder(c).expirationTime(Date.from(NOW)).build(),new JWTClaimsSet.Builder(c).expirationTime(Date.from(NOW.plusSeconds(61))).build(),new JWTClaimsSet.Builder(c).issuer("attacker").build()))assertThatThrownBy(()->ActorAssertion.verify(sign(key,altered),b,keys,Set.of(ISSUER),CLOCK)).hasMessage("ACTOR_TRUST_REJECTED");
  assertThatThrownBy(()->ActorAssertion.verify(token,b,Map.of("active",(ECPublicKey)pair().getPublic()),Set.of(ISSUER),CLOCK)).hasMessage("ACTOR_TRUST_REJECTED");
  assertThatThrownBy(()->ActorAssertion.verify(token,b,keys,Set.of(ISSUER),Clock.offset(CLOCK,Duration.ofSeconds(60)))).hasMessage("ACTOR_TRUST_REJECTED");
 }
 @Test void privateProofRequiresSocketWorkloadScopeAndMatchingActor()throws Exception{
  var key=pair();var route=PrivateWorkload.ROUTES.stream().filter(r->r.scope().equals("order-status.read_WITH_SIGNED_ACTOR")).findFirst().orElseThrow();String path=route.path().replace("{id}",UUID.randomUUID().toString());var b=new ActorAssertion.Binding(route.owner(),route.scope(),route.method(),path,UUID.randomUUID());var a=actor();String token=sign(key,ActorAssertion.claims(a,b,NOW));
  var guard=new PrivateWorkload("commerce",5,new PrivateIngress(List.of("127.0.0.1/32")),r->v->{if(!v.equals("valid-workload"))throw ActorAssertion.denied();return Jwt.withTokenValue(v).header("alg","RS256").subject("service-subject").claim("azp",r.client()).claim("scope",r.scope()).build();},Map.of("active",(ECPublicKey)key.getPublic()),Set.of(ISSUER),CLOCK);
  assertThat(guard.verify("127.0.0.1",route.method(),path,"Bearer valid-workload",token,b.correlation()).actor().party()).isEqualTo(a.party());
  for(String[] bad:List.of(new String[]{"192.0.2.1","Bearer valid-workload",token},new String[]{"127.0.0.1",null,token},new String[]{"127.0.0.1","Bearer invalid",token},new String[]{"127.0.0.1","Bearer valid-workload",null}))assertThatThrownBy(()->guard.verify(bad[0],route.method(),path,bad[1],bad[2],b.correlation())).hasMessage("ACTOR_TRUST_REJECTED");
  assertThatThrownBy(()->guard.match(route.method(),path+"%2f")).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->guard.match("GET",path)).hasMessage("ACTOR_TRUST_REJECTED");
 }
 @Test void phaseGatesAndAllCompiledRoutesMatch() {
  for(var route:PrivateWorkload.ROUTES){var guard=new PrivateWorkload(route.owner(),route.phase(),new PrivateIngress(List.of("127.0.0.1/32")),r->v->{throw ActorAssertion.denied();},Map.of(),Set.of(ISSUER),CLOCK);String path=route.path().replaceAll("\\{[^}]+\\}",UUID.randomUUID().toString());assertThat(guard.match(route.method(),path).api()).isEqualTo(route.api());if(route.phase()>1){var old=new PrivateWorkload(route.owner(),route.phase()-1,new PrivateIngress(List.of("127.0.0.1/32")),r->v->{throw ActorAssertion.denied();},Map.of(),Set.of(ISSUER),CLOCK);assertThatThrownBy(()->old.match(route.method(),path)).hasMessage("ACTOR_TRUST_REJECTED");}}
 }
 @Test void cidrNeverUsesForwardedHeadersOrDns(){var g=new PrivateIngress(List.of("10.2.0.0/16","2001:db8::/32"));assertThat(g.permits("10.2.4.5")).isTrue();assertThat(g.permits("10.3.4.5")).isFalse();assertThat(g.permits("localhost")).isFalse();assertThat(g.permits("2001:db8::1")).isTrue();assertThatThrownBy(()->new PrivateIngress(List.of("0.0.0.0/0"))).hasMessage("PRIVATE_CIDR_INVALID");}
 @Test void typedSessionEncryptionRejectsTamperingAndArbitraryObjects(){byte[] key=new byte[32];new SecureRandom().nextBytes(key);var c=new SessionCodec("v1",Map.of("v1",key));var s=new SessionRecord(1,UUID.randomUUID(),ISSUER,"user",NOW,null,"secret-access","secret-refresh",NOW.plusSeconds(300),JdbcSessionTrust.opaque(),SessionRecord.TokenState.READY);byte[] encoded=c.encode(s);assertThat(new String(encoded,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("secret-access","secret-refresh");assertThat(c.decode(encoded)).isEqualTo(s);assertThat(c.encode(s)).isNotEqualTo(encoded);byte[] bad=encoded.clone();bad[bad.length-2]^=1;assertThatThrownBy(()->c.decode(bad)).hasMessage("SESSION_CODEC_REJECTED");assertThatThrownBy(()->c.encode(new java.util.Date())).hasMessage("SESSION_CODEC_REJECTED");assertThatThrownBy(()->c.decode(new byte[]{(byte)0xac,(byte)0xed,0,5})).hasMessage("SESSION_CODEC_REJECTED");byte[] next=new byte[32];new SecureRandom().nextBytes(next);assertThat(new SessionCodec("v2",Map.of("v1",key,"v2",next)).decode(encoded)).isEqualTo(s);assertThatThrownBy(()->new SessionCodec("v2",Map.of("v2",next)).decode(encoded)).hasMessage("SESSION_CODEC_REJECTED");assertThat(s.toString()).doesNotContain("secret");}
 @Test void cookieAttributesCorsAndCsrfAreExact(){var cookies=new SessionCookies("test",false);var req=new MockHttpServletRequest();var res=new MockHttpServletResponse();cookies.write(req,res,UUID.randomUUID().toString());assertThat(res.getHeader("Set-Cookie")).contains("__Host-nigson-session=","Path=/","Secure","HttpOnly","SameSite=Lax").doesNotContain("Domain=");cookies.clear(req,res);assertThat(res.getHeaders("Set-Cookie").getLast()).contains("Max-Age=0");assertThatThrownBy(()->new SessionCookies("test",true)).hasMessage("INSECURE_COOKIE_LOCAL_ONLY");
  var cors=new BrowserBoundary("test",List.of("https://bff.synthetic.test"));req.addHeader("Origin","https://bff.synthetic.test");cors.check(req,res);assertThat(res.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");req=new MockHttpServletRequest();req.addHeader("Origin","https://bff.synthetic.test.attacker.test");var invalid=req;assertThatThrownBy(()->cors.check(invalid,new MockHttpServletResponse())).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->new BrowserBoundary("prod",List.of("http://localhost:8081"))).hasMessage("BFF_ORIGIN_INVALID");
 }
 @Test void staffMfaMustBeCurrentAndDoesNotTurnAdminIntoFinance(){var a=new ActorAssertion.Actor(UUID.randomUUID(),ISSUER,"staff",null,Set.of("ADMIN"),Set.of("ai.manage_WITH_SIGNED_ADMIN"),NOW.minusSeconds(899));ActorAssertion.recentMfa(a,CLOCK);assertThat(a.roles()).doesNotContain("FINANCE");assertThatThrownBy(()->ActorAssertion.recentMfa(a,Clock.offset(CLOCK,Duration.ofSeconds(1)))).hasMessage("ACTOR_TRUST_REJECTED");}
 @Test void kmsAndCookieProductionConfigurationNeverFallsBackToLocalTargets(){var config=new SecurityConfiguration();assertThatThrownBy(()->config.identityKms("local","eu-west-1","https://kms.eu-west-1.amazonaws.com")).hasMessage("LOCAL_KMS_TARGET_REQUIRED");assertThatThrownBy(()->config.identityKms("prod","eu-west-1","http://127.0.0.1:4566")).hasMessage("PRODUCTION_KMS_TARGET_REQUIRED");assertThatThrownBy(()->config.sessionCodec(null,"prod","v1","","",Base64.getEncoder().encodeToString(new byte[32]),"{}")).hasMessage("LOCAL_SESSION_KEY_FORBIDDEN");assertThatThrownBy(()->new WorkloadCredentials("test",ISSUER,"nigson-ai","synthetic-credential-with-32-bytes-length",1,null,CLOCK)).hasMessage("WORKLOAD_CREDENTIAL_POLICY_REQUIRED");}

}
