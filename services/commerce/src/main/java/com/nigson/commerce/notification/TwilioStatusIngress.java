package com.nigson.commerce.notification;

import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.commerce.security.IntegrationTarget;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Verifies the complete signed Twilio form against a fixed public URL, then durably relays a minimized receipt. */
public final class TwilioStatusIngress {
 private static final PrivateWorkload.Route ROUTE=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-189")&&r.owner().equals("integration")&&r.client().equals("nigson-commerce")).findFirst().orElseThrow();
 private final String account,token;private final URI publicUrl,target;private final Function<PrivateWorkload.Route,String> credentials;private final Clock clock;private final HttpClient http;
 public TwilioStatusIngress(String env,String account,String token,String publicUrl,String target,Function<PrivateWorkload.Route,String> credentials,Clock clock){
  if(!Set.of("local","test","dev","qa","stage","prod").contains(env)||account==null||!account.matches("AC[a-fA-F0-9]{32}")||token==null||token.length()<16||token.length()>512||publicUrl==null||publicUrl.length()>2048)throw new IllegalArgumentException("TWILIO_STATUS_CONFIGURATION_REQUIRED");
  URI uri;try{uri=URI.create(publicUrl);}catch(RuntimeException bad){throw new IllegalArgumentException("TWILIO_STATUS_URL_REQUIRED");}
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!"/api/v1/webhooks/notifications/twilio/status".equals(uri.getPath()))throw new IllegalArgumentException("TWILIO_STATUS_URL_REQUIRED");
  if(Set.of("local","test").contains(env)){if(!"http".equals(uri.getScheme())||!Set.of("localhost","127.0.0.1").contains(uri.getHost()))throw new IllegalArgumentException("TWILIO_STATUS_URL_REQUIRED");}else if(!"https".equals(uri.getScheme())||uri.getPort()!=-1)throw new IllegalArgumentException("TWILIO_STATUS_URL_REQUIRED");
  this.account=account;this.token=token;this.publicUrl=uri;this.target=IntegrationTarget.of(env,target);this.credentials=Objects.requireNonNull(credentials);this.clock=Objects.requireNonNull(clock);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 }
 public void receive(UUID job,byte[] raw,String signature,UUID correlation){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");if(job==null||job.equals(new UUID(0,0))||raw==null||raw.length>262144||signature==null||signature.length()>128||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();var params=form(raw);if(!CallbackSignatures.twilio(publicUrl+"?jobId="+job,params,signature,token))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  if(!account.equals(params.get("AccountSid")))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);String sid=params.get("MessageSid"),status=params.get("MessageStatus");if(sid==null||!sid.matches("SM[a-fA-F0-9]{32}")||status==null||!Set.of("accepted","scheduled","queued","sending","sent","delivered","read","failed","undelivered","canceled").contains(status))throw new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);
  String error=params.get("ErrorCode");if(error!=null&&!error.matches("[0-9]{3,6}"))error=null;String hash=EventCodec.hash(EventCodec.JSON.valueToTree(new TreeMap<>(params)));var body=EventCodec.JSON.createObjectNode();body.put("jobId",job.toString()).put("provider","TWILIO").put("accountSid",account).put("messageSid",sid).put("status",status).put("eventHash",hash);if(error!=null)body.put("errorCode",error);body.put("receivedAt",clock.instant().toString());relay(body,hash,correlation);
 }
 private void relay(tools.jackson.databind.JsonNode body,String key,UUID correlation){
  try{String credential=credentials.apply(ROUTE);if(credential==null||!credential.matches("[A-Za-z0-9._~+/-]{1,16384}={0,2}"))throw unavailable();var request=HttpRequest.newBuilder(target.resolve(ROUTE.path())).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+credential).header("Accept","application/json").header("Content-Type","application/json").header("Idempotency-Key",key).header("X-Correlation-ID",correlation.toString()).POST(HttpRequest.BodyPublishers.ofByteArray(EventCodec.JSON.writeValueAsBytes(body))).build();var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;try(var stream=response.body()){bytes=stream.readNBytes(65537);}if(response.statusCode()==404)throw new Problem.Failure(ProblemCode.NOT_FOUND);if(response.statusCode()==401||response.statusCode()==403)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(response.statusCode()!=200||bytes.length>65536||!"application/json".equals(response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip()))throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);var ack=EventCodec.JSON.readTree(bytes);if(!ack.isObject()||!ack.propertyNames().equals(Set.of("acknowledged"))||!ack.path("acknowledged").isBoolean()||!ack.path("acknowledged").asBoolean())throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
  }catch(Problem.Failure failure){throw failure;}catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
 }
 private static Map<String,String> form(byte[] raw){String input;try{input=StandardCharsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();}catch(CharacterCodingException bad){throw ApiValues.invalid();}if(input.isEmpty())throw ApiValues.invalid();var values=new TreeMap<String,String>();String[] pairs=input.split("&",-1);if(pairs.length>100)throw ApiValues.invalid();for(String pair:pairs){int split=pair.indexOf('=');if(split<=0)throw ApiValues.invalid();String key=decode(pair.substring(0,split)),value=decode(pair.substring(split+1));if(key.length()>100||value.length()>4096||values.putIfAbsent(key,value)!=null)throw ApiValues.invalid();}return Collections.unmodifiableMap(values);}
 private static String decode(String part){var out=new java.io.ByteArrayOutputStream();for(int i=0;i<part.length();i++){char c=part.charAt(i);if(c=='+')out.write(' ');else if(c=='%'){if(i+2>=part.length())throw ApiValues.invalid();int hi=Character.digit(part.charAt(++i),16),lo=Character.digit(part.charAt(++i),16);if(hi<0||lo<0)throw ApiValues.invalid();out.write((hi<<4)|lo);}else if(c<128)out.write(c);else throw ApiValues.invalid();}try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();}catch(CharacterCodingException bad){throw ApiValues.invalid();}}
 private static Problem.Failure unavailable(){return new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
}
