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
}
