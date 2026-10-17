package com.nigson.integration.notification;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
/** Ephemeral rendering and keyed request identity; no recipient/code in persistence or toString. */
public final class VerificationMessage {
 public record Request(String channel,String phone,String body,String contentSid,Map<String,String> variables,String callback,String hash,Instant expiresAt){public Request{variables=Map.copyOf(variables);}@Override public String toString(){return "VerificationRequest[protected]";}}
 private final byte[] key;private final URI callback;private final Map<String,Map<String,String>> bindings;
 public VerificationMessage(String environment,String callback,byte[] key,Map<String,Map<String,String>> bindings){
  if(key==null||key.length!=32||bindings==null||bindings.size()>100)throw new IllegalArgumentException("MESSAGE_PRIVACY_CONFIGURATION_REQUIRED");this.key=key.clone();var url=URI.create(callback);if(url.getHost()==null||url.getUserInfo()!=null||url.getQuery()!=null||url.getFragment()!=null||!"/api/v1/webhooks/notifications/twilio/status".equals(url.getPath())||!Set.of("local","test","dev","qa","stage","prod").contains(environment))throw new IllegalArgumentException("MESSAGE_CALLBACK_CONFIGURATION_REQUIRED");if(Set.of("local","test").contains(environment)){if(!"http".equals(url.getScheme())||!Set.of("127.0.0.1","localhost","commerce").contains(url.getHost()))throw new IllegalArgumentException("MESSAGE_LOCAL_CALLBACK_REQUIRED");}else if(!"https".equals(url.getScheme())||url.getPort()!=-1)throw new IllegalArgumentException("MESSAGE_PUBLIC_HTTPS_REQUIRED");this.callback=url;var copy=new HashMap<String,Map<String,String>>();for(var entry:bindings.entrySet()){var values=entry.getValue();if(!entry.getKey().matches("HX[a-fA-F0-9]{32}")||values==null||values.size()!=2||values.keySet().stream().anyMatch(k->!k.matches("[1-9]|1[0-9]|20"))||!new HashSet<>(values.values()).equals(Set.of("code","expiresAt")))throw new IllegalArgumentException("MESSAGE_WHATSAPP_BINDINGS_REQUIRED");copy.put(entry.getKey(),Map.copyOf(values));}this.bindings=Map.copyOf(copy);
 }
 public Request build(UUID job,String accountHash,JsonNode template,VerificationContextClient.Context context){
  if(job==null||accountHash==null||!accountHash.matches("[a-f0-9]{64}")||!context.channel().equals(template.path("channel").asString()))throw rule();String channel=context.channel(),body=template.path("body").asString(),content=template.path("providerTemplateId").isNull()?null:template.path("providerTemplateId").asString();VerificationRequestedHandler.validateTemplate(channel,body,content,template.path("variablesSchema"));if(!context.phone().matches("\\+[1-9][0-9]{6,14}")||!context.code().matches("[0-9]{6}"))throw rule();var values=Map.of("code",context.code(),"expiresAt",context.expiresAt().toString());var rendered=new TreeMap<String,String>();if(channel.equals("WHATSAPP")){var mapping=bindings.get(content);if(mapping==null)throw rule();mapping.forEach((k,v)->rendered.put(k,values.get(v)));body=null;}else{body=body.replace("{{code}}",context.code()).replace("{{expiresAt}}",context.expiresAt().toString());if(body.length()>1600)throw rule();content=null;}
  String target=callback+"?jobId="+job;var identity=EventCodec.JSON.createObjectNode();identity.put("v",1).put("jobId",job.toString()).put("accountHash",accountHash).put("templateHash",EventCodec.hash(template)).put("channel",channel).put("phone",context.phone()).put("body",body).put("contentSid",content).put("callback",target);identity.set("variables",EventCodec.JSON.valueToTree(rendered));try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));String hash=HexFormat.of().formatHex(mac.doFinal(("nigson-notification-request-v1\n"+EventCodec.JSON.writeValueAsString(identity)).getBytes(StandardCharsets.UTF_8)));return new Request(channel,context.phone(),body,content,rendered,target,hash,context.expiresAt());}catch(java.security.GeneralSecurityException unavailable){throw new IllegalStateException("MESSAGE_REQUEST_HASH_UNAVAILABLE");}
 }
 private static Problem.Failure rule(){return new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);}
}
