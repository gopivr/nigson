package com.nigson.experience.careers;
import java.util.UUID;import org.junit.jupiter.api.Test;import com.nigson.shared.security.ActorAssertion;import static org.assertj.core.api.Assertions.*;
class CareerActorBoundaryTest {
 @Test void actorBindingAllowsOnlyRegisteredStyleStaffFacadePaths(){assertThatCode(()->new ActorAssertion.Binding("experience","careers.read_WITH_SIGNED_ACTOR","GET","/api/v1/staff/career-applications",UUID.randomUUID())).doesNotThrowAnyException();assertThatThrownBy(()->new ActorAssertion.Binding("experience","careers.read_WITH_SIGNED_ACTOR","GET","/api/v1/career-applications",UUID.randomUUID())).isInstanceOf(RuntimeException.class);}
}
