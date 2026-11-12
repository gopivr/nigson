package com.nigson.integration.providers;

import org.junit.jupiter.api.Test;
import com.nigson.shared.security.ProviderHttp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAdapterModeTest {
 @Test void testModeCannotConfigureExternalSmtp() {
  assertThatThrownBy(()->new EmailAdapter("test",ProviderHttp.Mode.TEST,"smtp.example.org",587,"smtp-user","smtp-password","noreply@nigson.example"))
   .hasMessage("SMTP_TEST_MODE_DISABLED");
 }
}
