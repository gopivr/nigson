package com.nigson.commerce.engagement;
import com.nigson.commerce.security.*;
import com.nigson.shared.security.*;
import com.nigson.shared.events.EventCodec;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistributorRelayTest {
 JdbcSessionTrust sessions;CommerceAuthority authority;ActorDelegation delegation;SessionRecord record;ActorAssertion.Actor actor;HttpServer server;EngagementRelay relay;String endpoint;AtomicReference<String> body=new AtomicReference<>();AtomicReference<String> cookie=new AtomicReference<>();AtomicReference<String> key=new AtomicReference<>();
 @BeforeEach void setup()throws Exception {
  sessions=mock(JdbcSessionTrust.class);authority=mock(CommerceAuthority.class);delegation=mock(ActorDelegation.class);
  UUID party=UUID.randomUUID();record=new SessionRecord(1,party,"https://identity.example.invalid","subject",Instant.now(),null,"test-access","test-refresh",Instant.now().plusSeconds(300),"c".repeat(43),SessionRecord.TokenState.READY);
  actor=new ActorAssertion.Actor(party,record.issuer(),record.subject(),null,Set.of("CUSTOMER","SALES"),Set.of(),null);
  when(sessions.read("session",true)).thenReturn(record);when(authority.resolve(record,null,Set.of())).thenReturn(actor);
  when(delegation.issue(eq("session"),any(),isNull())).thenReturn("test-signed-actor");
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{try {
   assertThat(x.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-workload");assertThat(x.getRequestHeaders().getFirst("X-Nigson-Actor")).isEqualTo("test-signed-actor");
   cookie.set(x.getRequestHeaders().getFirst("Cookie"));key.set(x.getRequestHeaders().getFirst("Idempotency-Key"));body.set(new String(x.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   boolean post=x.getRequestMethod().equals("POST"),mine=x.getRequestURI().getPath().contains("/me/");int status=post?(x.getRequestURI().getPath().endsWith("/decision")?202:201):200;
   byte[] data=(mine?"[]":"{\"version\":0}").getBytes(java.nio.charset.StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,data.length);x.getResponseBody().write(data);x.close();
  }catch(Throwable failure){x.sendResponseHeaders(500,-1);x.close();}});
  server.start();endpoint="http://127.0.0.1:"+server.getAddress().getPort();relay=new EngagementRelay("test",endpoint,r->"test-workload",delegation,sessions,authority,3);
 }
 @AfterEach void stop(){if(server!=null)server.stop(0);}
 @Test void submissionUsesSignedBodyAndNeverForwardsBrowserCookie(){
  var input=EventCodec.JSON.readTree("{\"organizationName\":\"Demo business\",\"countryCode\":\"NG\",\"documentIds\":[]}");var result=relay.execute(actor,"session","API-092","/api/v1/distributor-applications",null,input,"test-request",null,record.csrfToken(),UUID.randomUUID());
  assertThat(result.status()).isEqualTo(201);assertThat(body.get()).isEqualTo(EventCodec.JSON.writeValueAsString(input));assertThat(cookie.get()).isNull();assertThat(key.get()).isEqualTo("test-request");
  verify(sessions).csrf(record,record.csrfToken());verify(delegation).issue(eq("session"),argThat(b->b.method().equals("POST")&&b.resource().equals("/api/v1/distributor-applications")&&b.bodyHash().equals(ActorAssertion.Binding.bodyHash(EventCodec.JSON.writeValueAsBytes(input)))),isNull());
 }
 @Test void myApplicationsPreservesArrayResponse(){assertThat(relay.execute(actor,"session","API-093","/api/v1/me/distributor-applications",null,null,null,null,null,UUID.randomUUID()).body().isArray()).isTrue();}
 @Test void decisionPreservesAcceptedStatus(){String path="/api/v1/staff/distributor-applications/"+UUID.randomUUID()+"/decision";assertThat(relay.execute(actor,"session","API-095",path,null,EventCodec.JSON.readTree("{}"),"test-request","\"0\"",record.csrfToken(),UUID.randomUUID()).status()).isEqualTo(202);verify(authority).requireRole(actor,"SALES");}
 @Test void wrongPathAndEarlierReleaseFailBeforeSigning(){
  assertThatThrownBy(()->relay.execute(actor,"session","API-092","/api/v1/staff/users",null,EventCodec.JSON.readTree("{}"),"test-request",null,record.csrfToken(),UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  var bp1=new EngagementRelay("test",endpoint,r->"test-workload",delegation,sessions,authority,1);assertThatThrownBy(()->bp1.execute(actor,"session","API-093","/api/v1/me/distributor-applications",null,null,null,null,null,UUID.randomUUID())).hasMessage("FEATURE_DISABLED");verifyNoInteractions(delegation);
 }
 @Test void staleActorCannotBorrowOtherSession(){var stranger=new ActorAssertion.Actor(UUID.randomUUID(),record.issuer(),record.subject(),null,actor.roles(),Set.of(),null);assertThatThrownBy(()->relay.execute(stranger,"session","API-093","/api/v1/me/distributor-applications",null,null,null,null,null,UUID.randomUUID())).hasMessage("ACCESS_DENIED");verifyNoInteractions(delegation);}
 @Test void adminReadsAndVersionedLeadPatchUseSignedOwnerRoutes(){
  var bp4=new EngagementRelay("test",endpoint,r->"test-workload",delegation,sessions,authority,4);
  for(var row:List.of(new String[]{"API-089","/api/v1/staff/leads","SALES"},new String[]{"API-208","/api/v1/staff/content","CONTENT_EDITOR"},new String[]{"API-110","/api/v1/staff/reports/engagement","ANALYST"})){
   assertThat(bp4.execute(actor,"session",row[0],row[1],null,null,null,null,null,UUID.randomUUID()).status()).isEqualTo(200);verify(authority,atLeastOnce()).requireRole(actor,row[2]);assertThat(cookie.get()).isNull();
  }
  var path="/api/v1/staff/leads/"+UUID.randomUUID();var input=EventCodec.JSON.readTree("{\"state\":\"IN_PROGRESS\",\"note\":\"Sales follow-up started\"}");
  assertThat(bp4.execute(actor,"session","API-091",path,null,input,"lead-update","\"7\"",record.csrfToken(),UUID.randomUUID()).status()).isEqualTo(200);
  verify(delegation).issue(eq("session"),argThat(b->b.method().equals("PATCH")&&b.resource().equals(path)),isNull());verify(sessions).csrf(record,record.csrfToken());
 }
}
