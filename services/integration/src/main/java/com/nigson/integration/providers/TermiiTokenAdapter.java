package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Termii-managed OTP protocol. Owning workflow stores pinId, expiry and replay state. */
public final class TermiiTokenAdapter {
 public record Challenge(String pinId) {}
 private final ProviderHttp http;private final String key,sender;
 public TermiiTokenAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String key,String sender){
  if(sender==null||!sender.matches("[A-Za-z0-9 ._-]{1,11}"))throw new IllegalArgumentException("TERMII_APPROVED_SENDER_REQUIRED");
  http=new ProviderHttp(environment,mode,endpoint,"termii-mock",Set.of("api.ng.termii.com","api.termii.com","v3.api.termii.com"),key);
  this.key=key;this.sender=sender;
 }
 private JsonNode call(String path,Map<String,Object> input){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  var body=new HashMap<String,Object>(input);body.put("api_key",key);
  var response=http.call("POST",path,"application/json",EventCodec.JSON.writeValueAsString(body),null,true,65536);
  try{var value=EventCodec.JSON.readTree(response.body());if(!value.isObject())throw new IllegalArgumentException();return value;}
  catch(RuntimeException invalid){throw new ProviderHttp.Failure("TERMII_RESPONSE_INVALID",true,response.status());}
 }
 public Challenge send(String destination,int expiryMinutes){
  if(destination==null||!destination.matches("\\+234[1-9][0-9]{9}")||expiryMinutes<1||expiryMinutes>10)throw new IllegalArgumentException("OTP_REQUEST_INVALID");
  var response=call("/api/sms/otp/send",Map.of("to",destination.substring(1),"from",sender,"channel","dnd","pin_type","NUMERIC","pin_attempts",3,"pin_time_to_live",expiryMinutes,"pin_length",6,"pin_placeholder","<OTP>","message_text","Your Nigson verification code is <OTP>. Do not share this code."));
  String id=response.path("pinId").asString();if(id.isEmpty())id=response.path("pin_id").asString();
  if(!id.matches("[A-Za-z0-9_-]{1,128}")||!response.path("smsStatus").asString().equals("Message Sent"))throw new ProviderHttp.Failure("TERMII_SEND_UNCONFIRMED",true,200);
  return new Challenge(id);
 }
 public boolean verify(String pinId,String code){
  if(pinId==null||!pinId.matches("[A-Za-z0-9_-]{1,128}")||code==null||!code.matches("[0-9]{6}"))throw new IllegalArgumentException("OTP_PROOF_INVALID");
  var response=call("/api/sms/otp/verify",Map.of("pin_id",pinId,"pin",code));
  if(!pinId.equals(response.path("pinId").asString()))throw new ProviderHttp.Failure("TERMII_PIN_MISMATCH",true,200);
  var verified=response.get("verified");if(verified==null)throw new ProviderHttp.Failure("TERMII_RESPONSE_INVALID",true,200);
  if(verified.isBoolean())return verified.asBoolean();
  if(verified.isString()&&Set.of("True","False","true","false").contains(verified.asString()))return Boolean.parseBoolean(verified.asString());
  throw new ProviderHttp.Failure("TERMII_RESPONSE_INVALID",true,200);
 }
}
