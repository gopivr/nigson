package com.nigson.experience.distributor;
import com.nigson.shared.security.*;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DistributorDetailTest {
 private PrivateWorkload.Proof proof(String role){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-177")).findFirst().orElseThrow();var actor=new ActorAssertion.Actor(UUID.randomUUID(),"https://test.invalid","test-staff",null,Set.of(role),Set.of(route.scope()),null);return new PrivateWorkload.Proof("nigson-commerce","test-workload",route,actor);}
 @Test void savedDecisionReasonIsReturnedSeparatelyFromTheReviewNote(){
  var jdbc=mock(JdbcTemplate.class);UUID id=UUID.randomUUID(),reviewer=UUID.randomUUID();
  var row=new HashMap<String,Object>();row.put("id",id);row.put("organization_name","Test business");row.put("country_code","NG");row.put("status","REJECTED");row.put("version",2L);row.put("reviewer_party_id",reviewer);row.put("review_reason","Documents inspected");row.put("decision_reason","Registration not accepted");
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));
  var service=new DistributorService(jdbc,null,null,Clock.systemUTC(),Set.of(),"test-only",null);
  var detail=service.detail(proof("SALES"),id);assertThat(detail.reviewReason()).isEqualTo("Documents inspected");assertThat(detail.decisionReason()).isEqualTo("Registration not accepted");assertThat(EventCodec.JSON.valueToTree(detail).path("decisionReason").asString()).isEqualTo("Registration not accepted");
  row.remove("decision_reason");assertThat(service.detail(proof("SALES"),id).decisionReason()).isNull();
 }
 @Test void unauthorizedActorsCannotReadDecisionReasons(){var jdbc=mock(JdbcTemplate.class);var service=new DistributorService(jdbc,null,null,Clock.systemUTC(),Set.of(),"test-only",null);assertThatThrownBy(()->service.detail(proof("CUSTOMER"),UUID.randomUUID())).hasMessage("ACCESS_DENIED");verifyNoInteractions(jdbc);}
}
