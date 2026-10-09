package com.nigson.commerce.security;
import com.nigson.shared.security.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.*;
/** Real code+PKCE, JDBC fixation/state, refresh and provider logout, in an isolated vendor stack. */
class OidcSessionAcceptanceTest {
 @Test void actualVendorCodeStateTypedSessionMfaWorkloadRefreshAndLogout()throws Exception {
  String fixture=System.getenv("NIGSON_SEC_IDENTITY_FIXTURE"),url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(fixture!=null&&url!=null,"SEC_IDENTITY_CHECK=true make security-check supplies isolated vendor and owner databases");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
  var tree=new tools.jackson.databind.json.JsonMapper().readTree(Files.readString(Path.of(fixture)));String issuer=tree.get("issuer").asString();var ca=CertificateFactory.getInstance("X.509");var ks=KeyStore.getInstance(KeyStore.getDefaultType());ks.load(null,null);try(var input=Files.newInputStream(Path.of(tree.get("ca").asString()))){ks.setCertificateEntry("ca",ca.generateCertificate(input));}var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);var tls=SSLContext.getInstance("TLS");tls.init(null,tm.getTrustManagers(),new SecureRandom());
  var clock=Clock.systemUTC();var oidc=new OidcTrust("test",issuer,"nigson-bff",tree.get("bff_secret").asString(),tree.get("callback").asString(),tls,clock);var ds=new DriverManagerDataSource(url+"?currentSchema=commerce","nigson_commerce_app","mock-commerce-runtime");var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));byte[] key=new byte[32];new SecureRandom().nextBytes(key);var codec=new SessionCodec("v1",Map.of("v1",key));var store=new JdbcSessionTrust(jdbc,tx,codec,clock,Set.of("/account"));var metrics=new SimpleMeterRegistry();
  // The test mapping represents the later COM-PARTY-001 port, not a production account seed.
  var lifecycle=new SessionLifecycle(store,oidc,(token)->{String i=token.issuer(),s=token.subject();UUID party=UUID.randomUUID();jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic identity fixture')",party,i,s,party+"@synthetic.invalid");return party;},clock,metrics);
  SessionRecord retail=null;
  for(String name:List.of("retail","staff")){
   var input=tree.get(name);var created=store.begin("/account");var flow=new PendingLogin(1,input.get("state").asString(),input.get("nonce").asString(),input.get("verifier").asString(),"/account",clock.instant().plusSeconds(120));jdbc.update("UPDATE commerce.spring_session_attributes a SET attribute_bytes=? FROM commerce.spring_session s WHERE a.session_primary_id=s.primary_id AND s.session_id=? AND a.attribute_name='nigson.login'",codec.encode(flow),created.sessionId());
   assertThat(oidc.authorization(flow,false).getQuery()).contains("code_challenge_method=S256");assertThatThrownBy(()->lifecycle.callback(created.sessionId(),"wrong",input.get("code").asString())).hasMessage("ACTOR_TRUST_REJECTED");
   String rotated=lifecycle.callback(created.sessionId(),flow.state(),input.get("code").asString());assertThat(rotated).isNotEqualTo(created.sessionId());assertThatThrownBy(()->store.read(created.sessionId(),false)).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->lifecycle.callback(created.sessionId(),flow.state(),input.get("code").asString())).hasMessage("ACTOR_TRUST_REJECTED");var record=store.read(rotated,false);assertThat(record.issuer()).isEqualTo(issuer);assertThat(record.toString()).isEqualTo("SessionRecord[protected]");
   if(name.equals("staff")){assertThat(record.mfaAt()).isNotNull();ActorAssertion.recentMfa(new CommerceAuthority(jdbc,clock,1).resolve(record,null,Set.of()),clock);}else{assertThat(record.mfaAt()).isNull();retail=record;}
   var refreshed=lifecycle.refresh(rotated);assertThat(refreshed.loginAt()).isEqualTo(record.loginAt());assertThat(refreshed.mfaAt()).isEqualTo(record.mfaAt());assertThatThrownBy(()->lifecycle.logout(rotated,"wrong")).hasMessage("ACTOR_TRUST_REJECTED");lifecycle.logout(rotated,refreshed.csrfToken());assertThatThrownBy(()->store.read(rotated,false)).hasMessage("ACTOR_TRUST_REJECTED");assertThatThrownBy(()->oidc.refresh(refreshed)).hasMessage("OIDC_TRUST_REJECTED");
  }
  var credentials=new WorkloadCredentials("test",issuer,"nigson-integration",tree.get("workload_secret").asString(),1,tls,clock);var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-118")).findFirst().orElseThrow();String workload=credentials.token(route);assertThat(credentials.token(route).equals(workload)).isTrue();var target=new PrivateWorkload("commerce",1,new PrivateIngress(List.of("127.0.0.1/32")),r->KeycloakIdentity.decoder(new KeycloakIdentity.Policy("test",issuer,issuer+"/protocol/openid-connect/certs","nigson-commerce",Set.of(r.client()),Set.of(r.scope())),clock,tls),Map.of(),Set.of(issuer),clock);
  assertThat(target.verify("127.0.0.1",route.method(),route.path(),"Bearer "+workload,null,UUID.randomUUID()).client()).isEqualTo("nigson-integration");var browserToken=retail.accessToken();assertThatThrownBy(()->target.verify("127.0.0.1",route.method(),route.path(),"Bearer "+browserToken,null,UUID.randomUUID())).hasMessage("ACTOR_TRUST_REJECTED");
 }
}
