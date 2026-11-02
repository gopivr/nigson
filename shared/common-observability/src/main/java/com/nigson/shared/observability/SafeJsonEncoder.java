package com.nigson.shared.observability;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
/** Allowlisted JSON log protocol. Arbitrary library messages/throwables are never serialized. */
public final class SafeJsonEncoder extends EncoderBase<ILoggingEvent> {
 private static final JsonMapper JSON=new JsonMapper();
 private static final Set<String> CALLBACK_STAGES=Set.of("session_cookie","pending_state","provider_exchange","identity_mapping","browser_boundary","credential_parameters","route_trust","browser_credentials","session_authority");
 private String service="UNKNOWN",environment="UNKNOWN",version="UNKNOWN";
 public void setService(String value){service=value.matches("nigson-(commerce|experience|integration)")?value:"UNKNOWN";}
 public void setEnvironment(String value){environment=Set.of("local","test","dev","qa","stage","prod").contains(value)?value:"UNKNOWN";}
 public void setVersion(String value){version=value.matches("[0-9]+\\.[0-9]+\\.[0-9]+")?value:"UNKNOWN";}
 public byte[] headerBytes(){return new byte[0];}public byte[] footerBytes(){return new byte[0];}
 public byte[] encode(ILoggingEvent event){
  var out=new LinkedHashMap<String,Object>();out.put("timestamp",Instant.ofEpochMilli(event.getTimeStamp()).toString());out.put("severity",event.getLevel().toString());out.put("service",service);out.put("environment",environment);out.put("version",version);
  var mdc=event.getMDCPropertyMap();out.put("trace_id",safe(mdc.get("trace_id"),"[a-f0-9]{32}"));out.put("span_id",safe(mdc.get("span_id"),"[a-f0-9]{16}"));out.put("correlation_id",safe(mdc.get("correlation_id"),"[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"));out.put("error_code",errorCode(mdc.get("error_code")));
  out.put("message",safeMessage(event));return (JSON.writeValueAsString(out)+"\n").getBytes(StandardCharsets.UTF_8);
 }
 private static String safeMessage(ILoggingEvent event){
  String raw=event.getMessage();
  if(Set.of("REQUEST_COMPLETE","REQUEST_FAILED","OUTBOX_RELAY_UNAVAILABLE","EVENT_POLL_UNAVAILABLE","AUDIT_PERSISTED").contains(raw))return raw;
  String formatted=event.getFormattedMessage();
  Matcher callback=Pattern.compile("BFF_CALLBACK_(?:FILTER_)?REJECTED stage=([a-z_]+)").matcher(formatted);
  if(callback.matches()&&CALLBACK_STAGES.contains(callback.group(1)))return formatted;
  Matcher provider=Pattern.compile("OIDC_PROVIDER_REJECTED operation=(token|logout) status=([45][0-9]{2})").matcher(formatted);
  if(provider.matches())return formatted;
  Matcher trust=Pattern.compile("OIDC_TRUST_DENIED category=(RESPONSE|ACCESS_SIGNATURE|ID_SIGNATURE|BINDING)").matcher(formatted);
  if(trust.matches())return formatted;
  return "OPERATOR_EVENT";
 }
 private static String errorCode(String value){return value!=null&&Set.of("VALIDATION_ERROR","AUTH_REQUIRED","ACCESS_DENIED","NOT_FOUND","CONFLICT","IDEMPOTENCY_HASH_MISMATCH","IDEMPOTENCY_IN_PROGRESS","VERSION_MISMATCH","BUSINESS_RULE_VIOLATION","INVALID_TRANSITION","FEATURE_DISABLED","RATE_LIMITED","PROVIDER_UNAVAILABLE","PROVIDER_OUTCOME_UNKNOWN","INTERNAL_ERROR","BODY_TOO_LARGE","UNSUPPORTED_MEDIA_TYPE","METHOD_NOT_ALLOWED","REQUEST_REJECTED","OUTBOX_PUBLISH_FAILED","EVENT_PROCESS_FAILED").contains(value)?value:null;}
 private static String safe(String value,String pattern){return value!=null&&value.matches(pattern)?value:null;}
}
