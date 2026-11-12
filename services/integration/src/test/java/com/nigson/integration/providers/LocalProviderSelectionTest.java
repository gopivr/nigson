package com.nigson.integration.providers;
import com.nigson.shared.security.ProviderHttp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class LocalProviderSelectionTest {
 @Test void termiiSelectionDoesNotRequireTwilioAndDummyCredentialsFailOnUse(){
  new ApplicationContextRunner().withUserConfiguration(TwilioConfiguration.class,PartnerConfiguration.class,ProviderConfiguration.class)
   .withPropertyValues("APP_ENV=local","NIGSON_PROVIDERS_ENABLED=true","NIGSON_MESSAGING_PROVIDER=TERMII","NIGSON_TERMII_ENABLED=true","TERMII_MODE=LIVE","TERMII_BASE_URL=https://api.ng.termii.com","TERMII_API_KEY=dummy-not-configured","TERMII_SENDER_ID=UNSET","EMAIL_MODE=LIVE","SMTP_HOST=unconfigured.invalid","SMTP_PORT=587","SMTP_USERNAME=dummy-not-configured","SMTP_PASSWORD=dummy-not-configured","SMTP_FROM=unconfigured@unconfigured.invalid")
   .run(context->{assertThat(context).hasNotFailed().doesNotHaveBean(TwilioAdapter.class).hasSingleBean(TermiiTokenAdapter.class).hasSingleBean(EmailAdapter.class);
    assertThatThrownBy(()->context.getBean(TermiiTokenAdapter.class).send("+2348012345678",5)).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.getMessage()).isEqualTo("TERMII_CREDENTIALS_NOT_CONFIGURED"));
    assertThatThrownBy(()->context.getBean(EmailAdapter.class).send("recipient@example.invalid","Test","Test")).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->assertThat(f.getMessage()).isEqualTo("SMTP_CREDENTIALS_NOT_CONFIGURED"));
   });
 }
}
