package com.nigson.integration.providers;
import java.util.Map;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ProviderHttp;
/** Nonsecret immutable transport/account/service identity; renewal of credentials preserves it. */
public final class TwilioAccount {
 private TwilioAccount(){}
 public static String fingerprint(ProviderHttp.Mode mode,String account,String service){if(mode==null||account==null||!account.matches("AC[a-fA-F0-9]{32}")||service==null||!service.matches("MG[a-fA-F0-9]{32}"))throw new IllegalArgumentException("TWILIO_ACCOUNT_IDENTITY_REQUIRED");return EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("provider","TWILIO","mode",mode.name(),"account",account,"service",service)));}
}
