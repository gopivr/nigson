package com.nigson.providers;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
import com.nigson.integration.providers.*;
import java.nio.file.*;
import java.util.*;

/** Explicit operator-invoked genuine, read-only HTTP qualification. No invented account/reference. */
public final class ProviderLiveQualification {
 private static String setting(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("MISSING_"+key);return value;}
 static void verifiedSettlement(tools.jackson.databind.JsonNode result){
  if(!result.path("status").asString().equals("success")||!result.path("id").isIntegralNumber()||result.path("id").asLong()<=0)throw new IllegalArgumentException("PAYSTACK_QUALIFICATION_REQUIRES_VERIFIED_SETTLEMENT");
  try{java.time.Instant.parse(result.path("paid_at").asString());}catch(RuntimeException invalid){throw new IllegalArgumentException("PAYSTACK_QUALIFICATION_SETTLEMENT_TIME_INVALID");}
 }
 public static void main(String[] args)throws Exception{
  if(!setting("ENV").equals("stage"))throw new IllegalArgumentException("LIVE_QUALIFICATION_REQUIRES_STAGE");
  String selected=System.getenv().getOrDefault("PROVIDER","all");
  if(!Set.of("all","paystack","fedex","twilio").contains(selected))throw new IllegalArgumentException("PROVIDER_INVALID");
  if(selected.equals("all")||selected.equals("paystack")){
   var mode=ProviderHttp.Mode.valueOf(setting("PAYSTACK_MODE"));if(mode!=ProviderHttp.Mode.TEST)throw new IllegalArgumentException("PAYSTACK_QUALIFICATION_REQUIRES_TEST_KEY");
   var client=new PaystackAdapter("stage",mode,setting("PAYSTACK_BASE_URL"),setting("PAYSTACK_SECRET_KEY"));
   var result=client.verify(setting("PAYSTACK_QUALIFICATION_REFERENCE"),Long.parseLong(setting("PAYSTACK_QUALIFICATION_KOBO")));
   verifiedSettlement(result);
  }
  if(selected.equals("all")||selected.equals("fedex")){
   var mode=ProviderHttp.Mode.valueOf(setting("FEDEX_MODE"));if(mode!=ProviderHttp.Mode.TEST)throw new IllegalArgumentException("FEDEX_QUALIFICATION_REQUIRES_SANDBOX");
   var client=new FedExAdapter("stage",mode,setting("FEDEX_BASE_URL"),setting("FEDEX_CLIENT_ID"),setting("FEDEX_CLIENT_SECRET"),setting("FEDEX_ACCOUNT_NUMBER"));
   String body=Files.readString(Path.of(setting("FEDEX_QUALIFICATION_REQUEST")));if(body.toLowerCase(Locale.ROOT).contains("synthetic"))throw new IllegalArgumentException("REAL_ACCOUNT_SHIPMENT_CONTEXT_REQUIRED");client.rates(EventCodec.JSON.readTree(body));
  }
  if(selected.equals("all")||selected.equals("twilio")){
   var mode=ProviderHttp.Mode.valueOf(setting("TWILIO_MODE"));if(mode==ProviderHttp.Mode.MOCK)throw new IllegalArgumentException("TWILIO_GENUINE_ACCOUNT_REQUIRED");
   var client=new TwilioAdapter("stage",mode,setting("TWILIO_BASE_URL"),setting("TWILIO_ACCOUNT_SID"),setting("TWILIO_AUTH_TOKEN"),setting("TWILIO_MESSAGING_SERVICE_SID"));client.status(setting("TWILIO_QUALIFICATION_MESSAGE_SID"));
  }
  System.out.println("PASS: selected genuine read-only provider HTTP contracts; financial, route, sender, template and release approvals require separate domain qualification");
 }
}
