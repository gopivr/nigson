package com.nigson.integration.providers;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.security.ProviderHttp;
/** Channel-specific private port; owners fence writes and persist outcomes. */
public interface WhatsAppProvider {
 ProviderHttp.Mode mode();
 String accountFingerprint();
 JsonNode whatsapp(String destination,String approvedContentSid,java.util.Map<String,String> variables,String callback);
 JsonNode status(String sid);
}
