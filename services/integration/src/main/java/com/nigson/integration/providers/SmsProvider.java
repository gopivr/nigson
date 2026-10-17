package com.nigson.integration.providers;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.security.ProviderHttp;
/** Channel-specific private port; owners fence writes and persist outcomes. */
public interface SmsProvider {
 ProviderHttp.Mode mode();
 String accountFingerprint();
 JsonNode sms(String destination,String body,String callback);
 JsonNode status(String sid);
}
