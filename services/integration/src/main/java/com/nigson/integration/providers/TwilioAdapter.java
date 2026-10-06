package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;

/** SMS/WhatsApp use one actual Messages HTTP API. Approved sender/template IDs are configuration. */
public final class TwilioAdapter {
 private final ProviderHttp http;private final String auth,account,service;
 public TwilioAdapter(String environment,ProviderHttp.Mode mode,String endpoint,String account,String token,String service) {
  if(account==null||!account.matches("AC[a-fA-F0-9]{32}")||service==null||!service.matches("MG[a-fA-F0-9]{32}"))throw new IllegalArgumentException("TWILIO_CONFIGURATION_INVALID");
  http=new ProviderHttp(environment,mode,endpoint,"twilio-mock",Set.of("api.twilio.com"),account,token,service);
  this.account=account;this.service=service;auth="Basic "+Base64.getEncoder().encodeToString((account+":"+token).getBytes(StandardCharsets.UTF_8));
 }
 private JsonNode call(String method,String path,Map<String,String> fields){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  var response=http.call(method,path,"application/x-www-form-urlencoded",fields==null?"":ProviderHttp.form(fields),auth);
  try{var json=EventCodec.JSON.readTree(response.body());if(!json.path("sid").asString().matches("SM[a-fA-F0-9]{32}")||!Set.of("accepted","scheduled","queued","sending","sent","delivered","undelivered","failed","canceled","read").contains(json.path("status").asString()))throw new IllegalArgumentException("TWILIO_RESPONSE_INVALID");return json;}catch(RuntimeException e){throw new ProviderHttp.Failure("TWILIO_RESPONSE_INVALID",!method.equals("GET"),response.status());}
 }
 public JsonNode sms(String destination,String body,String callback){return send(destination,body,null,null,callback);}
 public JsonNode whatsapp(String destination,String approvedContentSid,Map<String,String> variables,String callback){
  if(approvedContentSid==null||!approvedContentSid.matches("HX[a-fA-F0-9]{32}")||variables.size()>20)throw new IllegalArgumentException("TWILIO_TEMPLATE_INVALID");
  return send("whatsapp:"+destination,null,approvedContentSid,EventCodec.JSON.writeValueAsString(variables),callback);
 }
 private JsonNode send(String destination,String body,String content,String variables,String callback){
  if(destination==null||!destination.matches("(whatsapp:)?\\+[1-9][0-9]{6,14}"))throw new IllegalArgumentException("DESTINATION_INVALID");
  URI url=URI.create(callback);if(url.getUserInfo()!=null||url.getFragment()!=null||!Set.of("https","http").contains(url.getScheme()))throw new IllegalArgumentException("CALLBACK_CONFIGURATION_INVALID");
  var fields=new HashMap<String,String>();fields.put("To",destination);fields.put("MessagingServiceSid",service);fields.put("StatusCallback",callback);
  if(body!=null){if(body.isBlank()||body.length()>1600)throw new IllegalArgumentException("MESSAGE_INVALID");fields.put("Body",body);}else{fields.put("ContentSid",content);fields.put("ContentVariables",variables);}
  return call("POST","/2010-04-01/Accounts/"+account+"/Messages.json",fields);
 }
 public JsonNode status(String sid){if(sid==null||!sid.matches("SM[a-fA-F0-9]{32}"))throw new IllegalArgumentException("TWILIO_SID_INVALID");return call("GET","/2010-04-01/Accounts/"+account+"/Messages/"+sid+".json",null);}
}
