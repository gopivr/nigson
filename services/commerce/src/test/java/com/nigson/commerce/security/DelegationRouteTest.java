package com.nigson.commerce.security;
import org.junit.jupiter.api.Test;
import java.util.*;import java.time.*;
import com.nigson.shared.security.ActorAssertion;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class DelegationRouteTest {
 @Test void distinctResourcesMayShareAnApprovedScope(){
  var now=Instant.now();var id=UUID.randomUUID();var session=new SessionRecord(1,id,"https://identity.example.invalid","subject",now,null,"access","refresh",now.plusSeconds(300),"c".repeat(43),SessionRecord.TokenState.READY);
  var actor=new ActorAssertion.Actor(id,session.issuer(),session.subject(),null,Set.of("CONTENT_EDITOR"),Set.of("content.read_WITH_SIGNED_ACTOR"),null);
  var authority=spy(new CommerceAuthority(mock(JdbcTemplate.class),Clock.systemUTC(),4));
  doReturn(actor).when(authority).resolve(eq(session),isNull(),isNull(),eq(actor.scopes()));
  for(String resource:List.of("/api/v1/staff/content","/api/v1/staff/content-revisions/"+UUID.randomUUID()+"/preview"))assertThat(authority.forDelegation(session,new ActorAssertion.Binding("experience","content.read_WITH_SIGNED_ACTOR","GET",resource,UUID.randomUUID(),ActorAssertion.Binding.bodyHash(new byte[0])),null)).isEqualTo(actor);
  assertThatThrownBy(()->authority.forDelegation(session,new ActorAssertion.Binding("experience","content.read_WITH_SIGNED_ACTOR","GET","/api/v1/staff/leads",UUID.randomUUID(),ActorAssertion.Binding.bodyHash(new byte[0])),null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
}
