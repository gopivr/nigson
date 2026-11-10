package com.nigson.commerce.engagement;
import com.nigson.commerce.security.SessionCookies;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DistributorControllerTest {
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @Test void emptyQueueQueryIsAbsentRatherThanInvalidEmptyString(){
  var relay=mock(EngagementRelay.class);var cookies=mock(SessionCookies.class);var req=new MockHttpServletRequest("GET","/api/v1/staff/distributor-applications");
  var actor=new ActorAssertion.Actor(UUID.randomUUID(),"https://identity.example.invalid","subject",null,Set.of("SALES"),Set.of(),null);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));when(cookies.read(req)).thenReturn("session");
  when(relay.execute(eq(actor),eq("session"),eq("API-094"),eq(req.getRequestURI()),isNull(),isNull(),isNull(),isNull(),isNull(),any())).thenReturn(new EngagementRelay.Result(200,EventCodec.JSON.readTree("{\"content\":[]}")));
  var result=new EngagementController(relay,cookies).distributorQueue(req);assertThat(result.getStatusCode().value()).isEqualTo(200);assertThat(result.getBody().path("content").isArray()).isTrue();
  verify(relay).execute(eq(actor),eq("session"),eq("API-094"),eq(req.getRequestURI()),isNull(),isNull(),isNull(),isNull(),isNull(),any());
 }
 @Test void quoteRequestEndpointsRelayCreateAndOwnerHistory(){
  var relay=mock(EngagementRelay.class);var cookies=mock(SessionCookies.class);var controller=new EngagementController(relay,cookies);String path="/api/v1/quote-requests";
  var actor=new ActorAssertion.Actor(UUID.randomUUID(),"https://identity.example.invalid","customer",null,Set.of("CUSTOMER"),Set.of(),null);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));
  var get=new MockHttpServletRequest("GET",path);when(cookies.read(get)).thenReturn("session");
  when(relay.execute(eq(actor),eq("session"),eq("API-212"),eq(path),isNull(),isNull(),isNull(),isNull(),isNull(),any())).thenReturn(new EngagementRelay.Result(200,EventCodec.JSON.readTree("{\"items\":[]}")));
  var history=controller.myQuoteRequests(get);assertThat(history.getStatusCode().value()).isEqualTo(200);assertThat(history.getBody().path("items").isArray()).isTrue();
  var post=new MockHttpServletRequest("POST",path);post.addHeader("Idempotency-Key","request-key");post.addHeader("X-CSRF-TOKEN","c".repeat(43));when(cookies.read(post)).thenReturn("session");var body=EventCodec.JSON.readTree("{\"subject\":\"Bulk order\"}");
  when(relay.execute(eq(actor),eq("session"),eq("API-088"),eq(path),isNull(),eq(body),eq("request-key"),isNull(),eq("c".repeat(43)),any())).thenReturn(new EngagementRelay.Result(201,EventCodec.JSON.readTree("{\"leadId\":\""+UUID.randomUUID()+"\",\"state\":\"NEW\"}")));
  var created=controller.createQuoteRequest(post,body);assertThat(created.getStatusCode().value()).isEqualTo(201);
  verify(relay).execute(eq(actor),eq("session"),eq("API-212"),eq(path),isNull(),isNull(),isNull(),isNull(),isNull(),any());
  verify(relay).execute(eq(actor),eq("session"),eq("API-088"),eq(path),isNull(),eq(body),eq("request-key"),isNull(),eq("c".repeat(43)),any());
 }
}
