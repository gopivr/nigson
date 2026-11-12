package com.nigson.integration.providers;

import org.junit.jupiter.api.Test;
import com.nigson.shared.security.ProviderHttp;

import static org.assertj.core.api.Assertions.*;

class ProviderHttpModeTest {
 @Test void dummyLocalCredentialsFailOnAccessWithoutSendingHttp(){
  var http=new ProviderHttp("local",ProviderHttp.Mode.LIVE,"https://api.ng.termii.com","termii-mock",java.util.Set.of("api.ng.termii.com"),"dummy-not-configured");
  assertThatThrownBy(()->http.call("POST","/api/sms/send","application/json","{}",null)).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->{assertThat(f.getMessage()).isEqualTo("TERMII_CREDENTIALS_NOT_CONFIGURED");assertThat(f.uncertain).isFalse();});
 }
 @Test void localTestModeIsAllowedButProductionStillRequiresLive(){
  assertThat(new ProviderHttp("local",ProviderHttp.Mode.TEST,"https://api.paystack.co","paystack-mock",java.util.Set.of("api.paystack.co"),"sk_test_abcdef0123456789")).isNotNull();
  assertThatThrownBy(()->new ProviderHttp("prod",ProviderHttp.Mode.TEST,"https://api.paystack.co","paystack-mock",java.util.Set.of("api.paystack.co"),"sk_test_abcdef0123456789")).hasMessage("LIVE_PROVIDER_MODE_REQUIRED");
 }
 @Test void localDummySmtpFailsOnlyWhenSending(){
  var smtp=new EmailAdapter("local",ProviderHttp.Mode.LIVE,"unconfigured.invalid",587,"dummy-username","dummy-password","unconfigured@unconfigured.invalid");
  assertThatThrownBy(()->smtp.send("recipient@example.invalid","Test","Test")).isInstanceOfSatisfying(ProviderHttp.Failure.class,f->{assertThat(f.getMessage()).isEqualTo("SMTP_CREDENTIALS_NOT_CONFIGURED");assertThat(f.uncertain).isFalse();});
 }
 @Test void sandboxModeCannotSendExternalProviderRequests() {
  var http=new ProviderHttp("test",ProviderHttp.Mode.TEST,"https://api.paystack.co","paystack-mock",java.util.Set.of("api.paystack.co"),"sk_test_abcdef0123456789");

  assertThatThrownBy(()->http.call("GET","/transaction/verify/reference","application/json","",""))
   .isInstanceOfSatisfying(ProviderHttp.Failure.class,failure->{
    assertThat(failure.getMessage()).isEqualTo("SANDBOX_PROVIDER_CALL_DISABLED");
    assertThat(failure.uncertain).isFalse();
    assertThat(failure.status).isZero();
   });
 }
}
