package com.nigson.integration.notification;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.nigson.shared.security.ProviderHttp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationRequestedHandlerModeTest {
 private static final String ACCOUNT="AC"+"a".repeat(32);
 private static final String SERVICE="MG"+"b".repeat(32);
 private static final UUID GENERATION=UUID.randomUUID();

 @Test void nonTestRuntimesRequireLiveProviderMode() {
  assertRejected("local",ProviderHttp.Mode.MOCK);
  assertRejected("local",ProviderHttp.Mode.TEST);
  assertRejected("stage",ProviderHttp.Mode.TEST);
  assertRejected("prod",ProviderHttp.Mode.MOCK);
 }

 private static void assertRejected(String environment,ProviderHttp.Mode mode) {
  assertThatThrownBy(()->new VerificationRequestedHandler(null,Clock.systemUTC(),environment,mode,"en-NG",Map.of("SMS",1,"WHATSAPP",1),ACCOUNT,SERVICE,GENERATION))
   .hasMessage("MESSAGE_INTAKE_POLICY_REQUIRED");
 }
}
