package com.nigson.mocks;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import wiremock.com.fasterxml.jackson.databind.*;
import wiremock.com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Test-only bounded provider state, persisted before delayed responses. No production classpath. */
public final class ProviderStateExtension implements ResponseDefinitionTransformerV2 {
 private static final ObjectMapper JSON=new ObjectMapper();
 private final String provider=Objects.requireNonNull(System.getenv("MOCK_PROVIDER"));
 private final Path file=Path.of("/home/wiremock/state/provider.json");
 private ObjectNode state;
 public String getName(){return "nigson-provider-state";}
 public ProviderStateExtension(){
  if(!Set.of("local","test").contains(System.getenv("APP_ENV")))throw new IllegalArgumentException("MOCK_ENVIRONMENT_INVALID");
  if(!Set.of("paystack","fedex","twilio").contains(provider)) throw new IllegalArgumentException("MOCK_PROVIDER_INVALID");
  try{Files.createDirectories(file.getParent());state=Files.exists(file)?(ObjectNode)JSON.readTree(Files.readString(file)):fresh();}catch(Exception e){throw new IllegalStateException("MOCK_STATE_UNAVAILABLE");}
 }
 private ObjectNode fresh(){var n=JSON.createObjectNode();n.put("sequence",0);n.put("scenario","success");n.set("operations",JSON.createObjectNode());n.set("journal",JSON.createArrayNode());n.set("callbacks",JSON.createArrayNode());return n;}
 private void save() throws Exception {Path tmp=file.resolveSibling("provider.tmp");Files.writeString(tmp,JSON.writeValueAsString(state));Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
 private ResponseDefinition reply(int status,Object value,int delay) throws Exception {return new ResponseDefinitionBuilder().withStatus(status).withHeader("Content-Type","application/json").withBody(JSON.writeValueAsString(value)).withFixedDelay(delay).build();}
 private static Map<String,String> form(String body){var result=new TreeMap<String,String>();for(String p:body.split("&")){var kv=p.split("=",2);result.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length==2?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"");}return result;}
 private String next(String prefix){int n=state.path("sequence").asInt()+1;state.put("sequence",n);return prefix+String.format("%032d",n);}
 private ObjectNode operations(){return (ObjectNode)state.get("operations");}
 @Override public synchronized ResponseDefinition transform(ServeEvent served){
  var request=served.getRequest();String path=URI.create(request.getUrl()).getPath();String method=request.getMethod().getName();
  try {
   if(request.getBody().length>65536)return reply(413,Map.of("code","BODY_TOO_LARGE"),0);
   if(path.equals("/__scenario/reset")&&method.equals("POST")){state=fresh();save();return reply(200,Map.of("reset",true),0);}
   if(path.equals("/__scenario/state")&&method.equals("GET"))return reply(200,state,0);
   if(path.equals("/__scenario/configure")&&method.equals("POST")){
    var input=JSON.readTree(request.getBodyAsString());String scenario=input.path("scenario").asText();
    if(!Set.of("success","decline","rate-limit","unavailable","timeout-after-acceptance","booking-response-lost","duplicate-status","out-of-order-status","invalid-verification","invalid-refund-verification","wrong-currency","async-booking").contains(scenario))return reply(400,Map.of("code","SCENARIO_INVALID"),0);
    state.put("scenario",scenario);save();return reply(200,Map.of("scenario",scenario),0);
   }
   if(path.equals("/__scenario/progress")&&method.equals("POST")){
    var input=JSON.readTree(request.getBodyAsString());var op=operations().get(input.path("id").asText());String status=input.path("status").asText();
    if(op==null||!Set.of("success","failed","pending","processed","queued","sent","delivered","undelivered","cancelled","in_transit").contains(status))return reply(400,Map.of("code","PROGRESSION_INVALID"),0);
    if(provider.equals("fedex")&&!status.equals(op.path("status").asText()))((ObjectNode)op).put("scanAt",java.time.Instant.now().toString());((ObjectNode)op).put("status",status);if(provider.equals("paystack")&&status.equals("success")&&!op.has("transaction")&&!op.has("paid_at"))((ObjectNode)op).put("paid_at",java.time.Instant.now().toString());save();return reply(200,op,0);
   }
   if(path.equals("/__scenario/inbound")&&method.equals("POST")){
    if(!provider.equals("twilio")||operations().size()>=128)return reply(400,Map.of("code","INBOUND_SCENARIO_INVALID"),0);
    String sid=next("SM");operations().set(sid,JSON.createObjectNode().put("sid",sid).put("status","received"));save();return reply(200,Map.of("sid",sid),0);
   }
   if(path.equals("/__scenario/callback")&&method.equals("POST"))return callback(JSON.readTree(request.getBodyAsString()));
   if(path.startsWith("/__scenario/"))return reply(404,Map.of("code","ADMIN_ROUTE_UNKNOWN"),0);
   // No headers, bodies, recipient addresses, query strings or credential values enter journal.
   var journal=(ArrayNode)state.get("journal");if(journal.size()>=128)journal.remove(0);
   journal.add(JSON.createObjectNode().put("method",method).put("route",route(path)).put("sequence",state.path("sequence").asInt()));
   if(operations().size()>=128&&method.equals("POST")&&(path.equals("/transaction/initialize")||path.equals("/refund")||path.equals("/ship/v1/shipments")||path.endsWith("/Messages.json"))){save();return reply(429,Map.of("code","STATE_CAPACITY"),0);}
   String auth=request.getHeader("Authorization");
   boolean authorized=provider.equals("paystack")?"Bearer sk_mock_nigson".equals(auth):provider.equals("fedex")?(path.equals("/oauth/token")||"Bearer mock-fedex-access-token".equals(auth)): ("Basic "+Base64.getEncoder().encodeToString(("AC"+"0".repeat(32)+":mock-twilio-token").getBytes(StandardCharsets.UTF_8))).equals(auth);
   if(!authorized){save();return reply(401,Map.of("code","AUTHENTICATION_FAILED"),0);}
   String scenario=state.path("scenario").asText();if(scenario.equals("booking-response-lost"))scenario="timeout-after-acceptance";if(method.equals("POST")&&!path.equals("/oauth/token")&&Set.of("decline","rate-limit","unavailable").contains(scenario)){
    int code=scenario.equals("rate-limit")?429:scenario.equals("unavailable")?503:422;save();return reply(code,Map.of("status",false,"code","MOCK_"+scenario.toUpperCase(Locale.ROOT)),0);
   }
   ResponseDefinition response=provider.equals("paystack")?paystack(method,path,request.getUrl(),request.getBodyAsString(),scenario):provider.equals("fedex")?fedex(method,path,request.getBodyAsString(),scenario):twilio(method,path,request.getBodyAsString(),scenario);
   save();return response;
  } catch(wiremock.com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException invalid){return new ResponseDefinitionBuilder().withStatus(400).withHeader("Content-Type","application/json").withBody("{\"code\":\"MOCK_REQUEST_INVALID\"}").build();}
   catch(Exception unavailable){return new ResponseDefinitionBuilder().withStatus(503).withHeader("Content-Type","application/json").withBody("{\"code\":\"MOCK_STATE_OR_TRANSPORT_UNAVAILABLE\"}").build();}
 }
 private String route(String path){if(path.startsWith("/refund/"))return "/refund/{id}";if(path.startsWith("/transaction/verify/"))return "/transaction/verify/{reference}";if(path.matches("/2010-04-01/Accounts/[^/]+/Messages/[^/]+.json"))return "/2010-04-01/Accounts/{account}/Messages/{sid}.json";if(path.startsWith("/2010-04-01/Accounts/"))return "/2010-04-01/Accounts/{account}/Messages.json";return Set.of("/transaction/initialize","/refund","/oauth/token","/rate/v1/rates/quotes","/ship/v1/shipments","/ship/v1/shipments/cancel","/ship/v1/shipments/results","/track/v1/trackingnumbers","/track/v1/referencenumbers").contains(path)?path:"UNKNOWN";}
 private ResponseDefinition paystack(String method,String path,String url,String body,String scenario) throws Exception {
  if(method.equals("POST")&&path.equals("/transaction/initialize")){
   var input=JSON.readTree(body);String ref=input.path("reference").asText();long amount=input.path("amount").asLong();
   if(!ref.matches("[a-zA-Z0-9.=_-]{1,100}")||amount<=0||!input.path("currency").asText().equals("NGN")||!input.path("email").asText().contains("@"))return reply(400,Map.of("status",false,"message","Invalid initialization"),0);
   if(operations().has(ref))return reply(400,Map.of("status",false,"message","Duplicate reference"),0);
   var op=JSON.createObjectNode().put("id",next("TX")).put("reference",ref).put("domain","test").put("amount",amount).put("currency","NGN").put("status","pending");operations().set(ref,op);
   return reply(200,Map.of("status",true,"message","Authorization URL created","data",Map.of("reference",ref,"access_code","mock-access-"+state.path("sequence").asInt(),"authorization_url","http://paystack-mock:8080/hosted/"+ref)),scenario.equals("timeout-after-acceptance")?12000:0);
  }
  if(method.equals("GET")&&path.startsWith("/transaction/verify/")){var op=operations().get(path.substring(20));if(op!=null&&op.path("status").asText().equals("success")&&scenario.equals("invalid-verification")){op=op.deepCopy();((ObjectNode)op).put("paid_at","invalid-offline-timestamp");}return reply(op==null?404:200,op==null?Map.of("status",false):Map.of("status",true,"data",op),0);}
  if(method.equals("POST")&&path.equals("/refund")){
   var input=JSON.readTree(body);var transaction=operations().get(input.path("transaction").asText());long amount=input.path("amount").asLong();
   if(transaction==null||!transaction.path("status").asText().equals("success")||amount<=0)return reply(422,Map.of("status",false),0);
   long refunded=0;for(var op:operations())if(op.path("transaction").asText().equals(transaction.path("id").asText()))refunded+=op.path("amount").asLong();
   if(refunded+amount>transaction.path("amount").asLong())return reply(422,Map.of("status",false),0);
   String id=next("RF");var op=JSON.createObjectNode().put("id",id).put("transaction",transaction.path("id").asText()).put("amount",amount).put("currency","NGN").put("domain","test").put("integration",1).put("merchant_note",input.path("merchant_note").asText()).put("status","pending");operations().set(id,op);
   return reply(200,Map.of("status",true,"data",op),scenario.equals("timeout-after-acceptance")?12000:0);
  }
  if(method.equals("GET")&&path.startsWith("/refund/")){var op=operations().get(path.substring(8));if(op!=null&&scenario.equals("invalid-refund-verification")){op=op.deepCopy();((ObjectNode)op).put("amount",op.path("amount").asLong()+1);}return reply(op==null?404:200,op==null?Map.of("status",false):Map.of("status",true,"data",op),0);}
  if(method.equals("GET")&&path.equals("/refund")){var params=form(Objects.toString(URI.create(url).getRawQuery(),""));int page=Integer.parseInt(params.getOrDefault("page","1")),perPage=Integer.parseInt(params.getOrDefault("perPage","50"));if(page<1||page>10||perPage<1||perPage>100)return reply(400,Map.of("status",false),0);var matches=JSON.createArrayNode();for(var op:operations())if(op.has("transaction")&&(!params.containsKey("transaction")||op.path("transaction").asText().equals(params.get("transaction"))))matches.add(op);var list=JSON.createArrayNode();for(int n=(page-1)*perPage;n<Math.min(matches.size(),page*perPage);n++){var op=matches.get(n);if(scenario.equals("invalid-refund-verification")){op=op.deepCopy();((ObjectNode)op).put("amount",op.path("amount").asLong()+1);}list.add(op);}return reply(200,Map.of("status",true,"data",list),0);}
  return reply(404,Map.of("code","NOT_FOUND"),0);
 }
 private ResponseDefinition fedex(String method,String path,String body,String scenario) throws Exception {
  if(method.equals("POST")&&path.equals("/oauth/token")){var input=form(body);if(!input.getOrDefault("client_id","").equals("mock-fedex-client")||!input.getOrDefault("client_secret","").equals("mock-fedex-secret")||!input.getOrDefault("grant_type","").equals("client_credentials"))return reply(401,Map.of("error","invalid_client"),0);return reply(200,Map.of("access_token","mock-fedex-access-token","token_type","bearer","expires_in",3600),0);}
  var input=body.isBlank()?JSON.createObjectNode():JSON.readTree(body);
  if(method.equals("POST")&&path.equals("/rate/v1/rates/quotes"))return reply(200,Map.of("transactionId",next("RATE"),"output",Map.of("rateReplyDetails",List.of(Map.of("serviceType","FEDEX_INTERNATIONAL_PRIORITY","ratedShipmentDetails",List.of(Map.of("rateType","ACCOUNT","totalNetCharge",15000,"shipmentRateDetail",Map.of("currency",scenario.equals("wrong-currency")?"USD":"NGN"))))))),0);
  if(method.equals("POST")&&path.equals("/ship/v1/shipments")){
   if(!input.path("accountNumber").path("value").asText().equals("mock-fedex-account")||input.path("requestedShipment").isMissingNode())return reply(400,Map.of("errors",List.of(Map.of("code","INVALID_INPUT"))),0);
   var requested=input.path("requestedShipment");var packages=requested.path("requestedPackageLineItems");if(!packages.isArray()||packages.isEmpty()||packages.size()>100||!input.path("labelResponseOptions").asText().equals("LABEL"))return reply(400,Map.of("errors",List.of(Map.of("code","INVALID_INPUT"))),0);for(String field:List.of("shipper","recipients","pickupType","serviceType","packagingType","shippingChargesPayment","labelSpecification","totalWeight"))if(!requested.hasNonNull(field))return reply(400,Map.of("errors",List.of(Map.of("code","INVALID_INPUT"))),0);
   String id=next("TRACK");var op=JSON.createObjectNode().put("id",id).put("status","pending").put("scanAt",java.time.Instant.now().toString()).put("packages",packages.size()).put("reference",packages.path(0).path("customerReferences").path(0).path("value").asText());operations().set(id,op);if(scenario.equals("async-booking")){String job="JOB_"+id;op.put("jobId",job);return reply(200,Map.of("output",Map.of("jobId",job)),0);}return reply(200,Map.of("transactionId",id,"output",shipmentResult(id)),scenario.equals("timeout-after-acceptance")?12000:0);
  }
  if(method.equals("POST")&&path.equals("/ship/v1/shipments/results")){
   if(!input.path("accountNumber").path("value").asText().equals("mock-fedex-account"))return reply(400,Map.of("errors",List.of(Map.of("code","INVALID_INPUT"))),0);
   String job=input.path("jobId").asText();for(var op:operations())if(op.path("jobId").asText().equals(job)&&!job.isBlank())return reply(200,Map.of("output",op.path("status").asText().equals("pending")?Map.of("transactionShipments",List.of()):shipmentResult(op.path("id").asText())),0);
   return reply(404,Map.of("errors",List.of(Map.of("code","JOB_NOT_FOUND"))),0);
  }
  if(method.equals("PUT")&&path.equals("/ship/v1/shipments/cancel")){var op=operations().get(input.path("trackingNumber").asText());if(op==null)return reply(404,Map.of("errors",List.of(Map.of("code","TRACKING_NUMBER_NOT_FOUND"))),0);((ObjectNode)op).put("status","cancelled").put("scanAt",java.time.Instant.now().toString());return reply(200,Map.of("output",Map.of("cancelledShipment",true)),0);}
  if(method.equals("POST")&&path.equals("/track/v1/trackingnumbers")){var infos=input.path("trackingInfo");if(!infos.isArray()||infos.isEmpty()||infos.size()>30)return reply(400,Map.of("code","BATCH_INVALID"),0);var results=new ArrayList<Object>();var seen=new HashSet<String>();for(var info:infos){String id=info.path("trackingNumberInfo").path("trackingNumber").asText();if(!seen.add(id))return reply(400,Map.of("code","BATCH_DUPLICATE"),0);wiremock.com.fasterxml.jackson.databind.JsonNode found=null;for(var op:operations())for(int i=0;i<op.path("packages").asInt(1);i++)if(pieceId(op.path("id").asText(),i).equals(id))found=op;if(found==null)return reply(404,Map.of("code","NOT_FOUND"),0);String code=switch(found.path("status").asText()){case "in_transit"->"IT";case "delivered"->"DL";case "cancelled"->"CA";case "failed"->"SE";default->"OC";};String at=found.path("scanAt").asText();results.add(Map.of("trackingNumber",id,"trackResults",List.of(Map.of("trackingNumberInfo",Map.of("trackingNumber",id),"latestStatusDetail",Map.of("code",code),"scanEvents",List.of(Map.of("eventType",code,"date",at)),"dateAndTimes",code.equals("DL")?List.of(Map.of("type","ACTUAL_DELIVERY","dateTime",at)):List.of()))));}return reply(200,Map.of("output",Map.of("completeTrackResults",results)),0);}
  if(method.equals("POST")&&path.equals("/track/v1/referencenumbers")){
   var ref=input.path("referencesInformation");if(!ref.path("accountNumber").asText().equals("mock-fedex-account")||!ref.path("type").asText().equals("CUSTOMER_REFERENCE"))return reply(400,Map.of("errors",List.of(Map.of("code","INVALID_INPUT"))),0);
   var matches=new ArrayList<Map<String,Object>>();for(var op:operations())if(op.path("reference").asText().equals(ref.path("value").asText()))matches.add(Map.of("trackingNumber",op.path("id").asText(),"trackResults",List.of(Map.of("trackingNumberInfo",Map.of("trackingNumber",op.path("id").asText()),"latestStatusDetail",Map.of("code",op.path("status").asText())))));
   return reply(200,Map.of("output",Map.of("completeTrackResults",matches)),0);
  }
  return reply(404,Map.of("code","NOT_FOUND"),0);
 }
 private static String pieceId(String id,int i){return i==0?id:"P"+id.substring(5)+String.format(Locale.ROOT,"%03d",i);}
 private Map<String,Object> shipmentResult(String id){var pieces=new ArrayList<Object>();int count=operations().get(id).path("packages").asInt(1);for(int i=0;i<count;i++)pieces.add(Map.of("trackingNumber",pieceId(id,i),"packageDocuments",List.of(Map.of("contentType","LABEL","docType","PDF","encodedLabel",syntheticPdf()))));return Map.of("transactionShipments",List.of(Map.of("masterTrackingNumber",id,"serviceType","FEDEX_INTERNATIONAL_PRIORITY","pieceResponses",pieces)));}
 private static String syntheticPdf(){var pdf=new StringBuilder("%PDF-1.4\n% SYNTHETIC MOCK LABEL - NOT FOR SHIPPING\n");String[] objects={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 288 432] /Resources << >> >>"};var offsets=new ArrayList<Integer>();for(int i=0;i<objects.length;i++){offsets.add(pdf.length());pdf.append(i+1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");}int xref=pdf.length();pdf.append("xref\n0 4\n0000000000 65535 f \n");for(int offset:offsets)pdf.append(String.format(Locale.ROOT,"%010d 00000 n \n",offset));pdf.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");return Base64.getEncoder().encodeToString(pdf.toString().getBytes(StandardCharsets.US_ASCII));}
 private ResponseDefinition twilio(String method,String path,String body,String scenario) throws Exception {
  String root="/2010-04-01/Accounts/AC"+"0".repeat(32)+"/Messages";
  if(method.equals("POST")&&path.equals(root+".json")){
   var input=form(body);if(!input.containsKey("To")||!input.containsKey("MessagingServiceSid")||(!input.containsKey("Body")&&!input.containsKey("ContentSid")))return reply(400,Map.of("code",21602,"message","Invalid message"),0);
   String sid=next("SM");var op=JSON.createObjectNode().put("sid",sid).put("status","queued").put("error_code",(String)null);operations().set(sid,op);return reply(201,op,scenario.equals("timeout-after-acceptance")?12000:0);
  }
  if(method.equals("GET")&&path.startsWith(root+"/")&&path.endsWith(".json")){String sid=path.substring(root.length()+1,path.length()-5);var op=operations().get(sid);return reply(op==null?404:200,op==null?Map.of("code",20404):op,0);}
  return reply(404,Map.of("code","NOT_FOUND"),0);
 }
 private ResponseDefinition callback(JsonNode input) throws Exception {
  String id=input.path("id").asText();var op=operations().get(id);String url=input.path("url").asText();int repeat=input.path("repeat").asInt(state.path("scenario").asText().equals("duplicate-status")?2:1);
  if(op==null||repeat<1||repeat>3||!callbackTarget(url))return reply(400,Map.of("code","CALLBACK_INVALID"),0);
  if(provider.equals("fedex"))return reply(400,Map.of("code","FEDEX_POLLING_ONLY"),0);
  String canonical=input.path("external_url").asText(url);
  URI transport=URI.create(url),external=URI.create(canonical);
  if(!canonical.equals(url)&&(!external.getScheme().equals("http")||!Set.of("localhost","127.0.0.1").contains(external.getHost())||external.getPort()<1||external.getUserInfo()!=null||external.getFragment()!=null||!Objects.equals(external.getRawPath(),transport.getRawPath())||!Objects.equals(external.getRawQuery(),transport.getRawQuery())))return reply(400,Map.of("code","CALLBACK_CANONICAL_URL_INVALID"),0);
  String event=input.path("event").asText("charge.success");
  if(provider.equals("paystack")&&!Set.of("charge.success","refund.pending","refund.processed","refund.failed").contains(event))return reply(400,Map.of("code","CALLBACK_EVENT_INVALID"),0);
  boolean disorder=provider.equals("twilio")&&state.path("scenario").asText().equals("out-of-order-status");
  int count=disorder?2:repeat;
  var client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();int status=0;
  var history=state.has("callbacks")?(ArrayNode)state.get("callbacks"):state.putArray("callbacks");
  for(int n=0;n<count;n++){
   String body,header,signature;
   String deliveredStatus=disorder?(n==0?"delivered":"sent"):input.path("status").asText(op.path("status").asText());
   if(provider.equals("paystack")){body=JSON.writeValueAsString(Map.of("event",event,"data",op));header="x-paystack-signature";signature=HexFormat.of().formatHex(sign("HmacSHA512","sk_mock_nigson",body));}
   else {
    if(!input.has("inbound_body")&&!Set.of("queued","sent","delivered","undelivered","failed","read").contains(deliveredStatus))return reply(400,Map.of("code","CALLBACK_STATUS_INVALID"),0);
    var params=new TreeMap<String,String>();params.put("MessageSid",op.path("sid").asText());if(input.has("inbound_body")){String text=input.path("inbound_body").asText();if(text.length()>1600)return reply(400,Map.of("code","INBOUND_TOO_LARGE"),0);params.put("From","whatsapp:+2348000000001");params.put("To","whatsapp:+2348000000002");params.put("Body",text);}else params.put("MessageStatus",deliveredStatus);params.put("AccountSid","AC"+"0".repeat(32));StringBuilder signed=new StringBuilder(canonical);params.forEach((k,v)->signed.append(k).append(v));body=params.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));header="X-Twilio-Signature";signature=Base64.getEncoder().encodeToString(sign("HmacSHA1","mock-twilio-token",signed.toString()));
   }
   if(input.path("invalid_signature").asBoolean())signature="invalid";
   var response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(3)).header(header,signature).header("Content-Type",provider.equals("twilio")?"application/x-www-form-urlencoded":"application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding());status=response.statusCode();
   if(history.size()>=128)history.remove(0);history.add(JSON.createObjectNode().put("id",id).put("status",provider.equals("paystack")?event:deliveredStatus).put("response_status",status));
  }
  save();return reply(200,Map.of("deliveries",count,"response_status",status),0);
 }

 private boolean callbackTarget(String url){
  try{
   URI uri=URI.create(url);if(!uri.getScheme().equals("http")||uri.getUserInfo()!=null||uri.getFragment()!=null)return false;
   String route=uri.getHost()+":"+uri.getPort()+uri.getPath();
   Set<String> allowed=provider.equals("paystack")?Set.of("callback-receiver:8080/paystack","commerce:8081/api/v1/webhooks/payments/paystack"):Set.of("callback-receiver:8080/twilio","commerce:8081/api/v1/webhooks/notifications/twilio/status","experience:8082/api/v1/webhooks/notifications/twilio/whatsapp");
   return allowed.contains(route)&&(uri.getRawQuery()==null||uri.getRawQuery().matches("jobId=[a-fA-F0-9-]{36}"));
  }catch(RuntimeException invalid){return false;}
 }
 private static byte[] sign(String algorithm,String key,String body) throws Exception {var mac=Mac.getInstance(algorithm);mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),algorithm));return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));}
}
