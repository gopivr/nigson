package com.nigson.commerce.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ExperienceTargetTest {
 @Test void acceptsOwnerComposeHostAndRejectsWrongOwnerAndUntrustedTargets(){
  assertThat(ExperienceTarget.of("local","http://experience:8082").getHost()).isEqualTo("experience");
  for(String target:java.util.List.of("http://integration:8083","http://example.com","http://experience:8082/path","http://user:pass@experience:8082","http://experience:8082?x=1"))assertThatThrownBy(()->ExperienceTarget.of("local",target)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->ExperienceTarget.of("prod","http://experience:8082")).isInstanceOf(IllegalArgumentException.class);
  assertThat(ExperienceTarget.of("prod","https://experience.nigson.internal").getScheme()).isEqualTo("https");
 }
}
