package com.nigson.providers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import com.github.dockerjava.api.model.*;
import com.nigson.shared.security.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
import com.nigson.integration.providers.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Offline system harness. Containers are private to this run; published test ports bind loopback. */
public final class ProviderQualification {
 static {
  // The harness and actual Java HTTP adapters cannot contact external providers.
  ProxySelector.setDefault(new ProxySelector(){
   public List<Proxy> select(URI uri){
    if(Set.of("http","socket").contains(uri.getScheme())&&Set.of("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost()))return List.of(Proxy.NO_PROXY);
    throw new IllegalArgumentException("OFFLINE_HTTP_TARGET_DENIED");
   }
   public void connectFailed(URI uri,SocketAddress address,java.io.IOException failure){}
  });
 }
 private static final String IMAGE="wiremock/wiremock:3.13.2@sha256:0d4ecb3e4dc8213fd7a4d37d6a78f6e6b553a6d2e15bd51b0999781282ac61b3";
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private static tools.jackson.databind.JsonNode admin(String base,String operation,Map<String,Object> data)throws Exception{
  var request=HttpRequest.newBuilder(URI.create(base+"/__scenario/"+operation)).timeout(Duration.ofSeconds(15));
  var response=HTTP.send(data==null?request.GET().build():request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(EventCodec.JSON.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
  check(response.statusCode()==200,"Mock admin failure");return EventCodec.JSON.readTree(response.body());
 }
 private static GenericContainer<?> mock(String provider,org.testcontainers.containers.Network network){
  return new GenericContainer<>(DockerImageName.parse(IMAGE)).withEnv("MOCK_PROVIDER",provider).withEnv("APP_ENV","test")
   .withCopyFileToContainer(MountableFile.forHostPath(Path.of("tests/mocks/target/provider-state.jar")),"/var/wiremock/extensions/provider-state.jar")
   .withCommand("--extensions","com.nigson.mocks.ProviderStateExtension","--no-request-journal","--disable-request-logging")
   .withNetwork(network).withExposedPorts(8080).withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(8080))));
 }
 private static void check(boolean value,String code){if(!value)throw new AssertionError(code);}
 private static void failure(Runnable command,boolean uncertain){try{command.run();throw new AssertionError("Expected provider failure");}catch(ProviderHttp.Failure e){check(e.uncertain==uncertain,"Uncertainty classification mismatch");}}
 private static void rejected(Runnable command){try{command.run();throw new AssertionError("Unsafe mode accepted");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception {
  try{HTTP.send(HttpRequest.newBuilder(URI.create("https://api.paystack.co")).GET().build(),HttpResponse.BodyHandlers.discarding());throw new AssertionError("External HTTP permitted");}
  catch(IllegalArgumentException blocked){check("OFFLINE_HTTP_TARGET_DENIED".equals(blocked.getMessage()),"External HTTP did not fail at the offline boundary");}
  try(var network=org.testcontainers.containers.Network.newNetwork();var pay=mock("paystack",network);var fed=mock("fedex",network);var twilio=mock("twilio",network);var mail=new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.10@sha256:b1f1be18af530d939a11ee8820b379e0c88eeec204d904bfad68862adced3a5a")).withNetwork(network).withExposedPorts(1025,8025).withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(1025)),new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(8025))))){
   String root=Path.of("").toAbsolutePath().toString();
   String callbackClasspath=System.getProperty("java.class.path");
   try(var callback=new GenericContainer<>(DockerImageName.parse("nigson/migration:local"))
    .withNetwork(network).withNetworkAliases("callback-receiver")
    .withFileSystemBind(root+"/.build",root+"/.build",org.testcontainers.containers.BindMode.READ_ONLY)
    .withFileSystemBind(root+"/tests/providers/target",root+"/tests/providers/target",org.testcontainers.containers.BindMode.READ_ONLY)
    .withCreateContainerCmdModifier(cmd->cmd.withEntrypoint("java").withCmd("-cp",callbackClasspath,"com.nigson.providers.CallbackReceiver"))
    .withExposedPorts(8080).withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(8080))))) {
   callback.start();
   pay.start();fed.start();twilio.start();mail.start();
   String p="http://127.0.0.1:"+pay.getMappedPort(8080),f="http://127.0.0.1:"+fed.getMappedPort(8080),t="http://127.0.0.1:"+twilio.getMappedPort(8080);
   var adapter=new PaystackAdapter("test",ProviderHttp.Mode.MOCK,p,"sk_mock_nigson");
   check(PaystackAdapter.kobo("1234.56")==123456,"NGN kobo mismatch");rejected(()->PaystackAdapter.kobo("1.234"));
   org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
   try{adapter.initialize("forbidden-lock","synthetic@example.invalid","1.00");throw new AssertionError("Provider call inside transaction permitted");}catch(IllegalStateException guard){check(guard.getMessage().equals("PROVIDER_CALL_INSIDE_TRANSACTION"),"Wrong transaction guard");}finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
   adapter.initialize("order-1","synthetic@example.invalid","1234.56");check(adapter.verify("order-1",123456).path("status").asString().equals("pending"),"Paystack state not persisted");
   failure(()->adapter.initialize("order-1","synthetic@example.invalid","1234.56"),false);
   admin(p,"progress",Map.of("id","order-1","status","success"));check(adapter.verify("order-1",123456).path("status").asString().equals("success"),"Paystack progression");
   adapter.refund("order-1","234.56");check(adapter.refunds().size()==1,"Refund state");failure(()->adapter.refund("order-1","2000"),false);
   for(String scenario:List.of("decline","rate-limit","unavailable")){admin(p,"configure",Map.of("scenario",scenario));failure(()->adapter.initialize("error-"+scenario,"synthetic@example.invalid","1.00"),scenario.equals("unavailable"));}
   admin(p,"configure",Map.of("scenario","timeout-after-acceptance"));failure(()->adapter.initialize("uncertain-1","synthetic@example.invalid","1.00"),true);check(adapter.verify("uncertain-1",100).path("status").asString().equals("pending"),"Lost-response reconciliation");
   check(admin(p,"state",null).path("operations").size()==3,"Uncertain operation was blindly repeated");
   var carrier=new FedExAdapter("test",ProviderHttp.Mode.MOCK,f,"mock-fedex-client","mock-fedex-secret","mock-fedex-account");
   var shipment=EventCodec.JSON.readTree(Files.readString(Path.of("contracts/providers/fedex/ship-request.json")));
   check(carrier.rates(shipment).path("rateReplyDetails").isArray(),"Carrier rates");var booking=carrier.book(shipment);String tracking=booking.path("transactionShipments").path(0).path("masterTrackingNumber").asString();check(carrier.track(tracking).path("completeTrackResults").isArray(),"Carrier tracking");carrier.cancel(tracking);check(admin(f,"state",null).path("operations").path(tracking).path("status").asString().equals("cancelled"),"Carrier cancellation");
   admin(f,"configure",Map.of("scenario","timeout-after-acceptance"));failure(()->carrier.book(shipment),true);check(admin(f,"state",null).path("operations").size()==2,"Carrier uncertain booking repeated");
   admin(f,"configure",Map.of("scenario","decline"));failure(()->carrier.book(shipment),false);
   admin(f,"configure",Map.of("scenario","rate-limit"));failure(()->carrier.rates(shipment),false);
   admin(f,"configure",Map.of("scenario","unavailable"));failure(()->carrier.book(shipment),true);
   failure(()->new FedExAdapter("test",ProviderHttp.Mode.MOCK,f,"mock-fedex-client","mock-invalid-secret","mock-fedex-account").rates(shipment),false);
   var messaging=new TwilioAdapter("test",ProviderHttp.Mode.MOCK,t,"AC"+"0".repeat(32),"mock-twilio-token","MG"+"0".repeat(32));
   String sid=messaging.sms("+2348000000001","Synthetic fixture","http://callback-receiver:8080/twilio").path("sid").asString();
   admin(t,"progress",Map.of("id",sid,"status","delivered"));check(messaging.status(sid).path("status").asString().equals("delivered"),"SMS progression");
   messaging.whatsapp("+2348000000001","HX"+"0".repeat(32),Map.of("1","Synthetic"),"http://callback-receiver:8080/twilio");
   admin(t,"configure",Map.of("scenario","timeout-after-acceptance"));failure(()->messaging.sms("+2348000000001","Synthetic fixture","http://callback-receiver:8080/twilio"),true);check(admin(t,"state",null).path("operations").size()==3,"Message uncertain send repeated");
   for(String scenario:List.of("decline","rate-limit","unavailable")){admin(t,"configure",Map.of("scenario",scenario));failure(()->messaging.sms("+2348000000001","Synthetic fixture","http://callback-receiver:8080/twilio"),scenario.equals("unavailable"));}
   failure(()->new TwilioAdapter("test",ProviderHttp.Mode.MOCK,t,"AC"+"0".repeat(32),"mock-invalid-token","MG"+"0".repeat(32)).status(sid),false);
   for(String base:List.of(p,f,t)){String journal=admin(base,"state",null).path("journal").toString();check(!journal.contains("mock-")&&!journal.contains("synthetic@example.invalid")&&!journal.contains("+234")&&!journal.contains("Authorization"),"Journal contains credentials/PII");}
   rejected(()->new PaystackAdapter("prod",ProviderHttp.Mode.MOCK,p,"sk_mock_nigson"));rejected(()->new PaystackAdapter("test",ProviderHttp.Mode.TEST,"https://api.paystack.co","sk_mock_nigson"));
   rejected(()->new FedExAdapter("prod",ProviderHttp.Mode.LIVE,"https://apis.fedex.com","mock-client","mock-secret","mock-account"));
   rejected(()->new TwilioAdapter("prod",ProviderHttp.Mode.LIVE,"https://api.twilio.com","AC"+"0".repeat(32),"mock-token","MG"+"0".repeat(32)));
   // Configuration validation performs no network call and confirms genuine mode uses the same implementation.
   new PaystackAdapter("stage",ProviderHttp.Mode.TEST,"https://api.paystack.co","sk_test_configuration_validation_only");
   admin(p,"callback",Map.of("id","order-1","url","http://callback-receiver:8080/paystack","repeat",2));
   var rejectedSignature=admin(p,"callback",Map.of("id","order-1","url","http://callback-receiver:8080/paystack","invalid_signature",true));check(rejectedSignature.path("response_status").asInt()==403,"Invalid Paystack signature accepted");
   admin(t,"callback",Map.of("id",sid,"url","http://callback-receiver:8080/twilio","status","delivered","repeat",2));
   admin(t,"callback",Map.of("id",sid,"url","http://callback-receiver:8080/twilio","status","sent")); // intentional out-of-order callback
   check(admin(t,"callback",Map.of("id",sid,"url","http://callback-receiver:8080/twilio","invalid_signature",true)).path("response_status").asInt()==403,"Invalid Twilio signature accepted");
   admin(t,"configure",Map.of("scenario","out-of-order-status"));
   check(admin(t,"callback",Map.of("id",sid,"url","http://callback-receiver:8080/twilio")).path("deliveries").asInt()==2,"Named out-of-order scenario did not dispatch");
   admin(t,"configure",Map.of("scenario","success"));
   String inboundSid=admin(t,"inbound",Map.of()).path("sid").asString();
   check(admin(t,"callback",Map.of("id",inboundSid,"url","http://callback-receiver:8080/twilio","inbound_body","STOP")).path("response_status").asInt()==204,"Inbound WhatsApp signature rejected");
   var callbacks=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+callback.getMappedPort(8080)+"/state")).GET().build(),HttpResponse.BodyHandlers.ofString());
   var verification=EventCodec.JSON.readTree(callbacks.body());check(verification.size()==10,"Duplicate/out-of-order dispatch count");check(verification.path(5).path("state").asString().equals("sent"),"Out-of-order fixture not delivered");
   String receiver="http://127.0.0.1:"+callback.getMappedPort(8080);
   for(String path:List.of("/paystack","/twilio")){
    var unsigned=HTTP.send(HttpRequest.newBuilder(URI.create(receiver+path)).POST(HttpRequest.BodyPublishers.ofString(path.equals("/paystack")?"{}":"MessageSid=SM00000000000000000000000000000000")).build(),HttpResponse.BodyHandlers.ofString());
    check(unsigned.statusCode()==403,"Missing callback signature accepted");
   }
   check(admin(t,"callback",Map.of("id",sid,"url","http://callback-receiver:8080/twilio","external_url","http://localhost:8080/twilio")).path("response_status").asInt()==403,"Twilio signature ignored canonical URL");
   var signatureParams=new TreeMap<String,String>(Map.of("AccountSid","AC"+"0".repeat(32),"MessageSid",sid,"MessageStatus","delivered"));
   String canonical="http://callback-receiver:8080/twilio";
   var signatureInput=new StringBuilder(canonical);signatureParams.forEach((key,value)->signatureInput.append(key).append(value));
   var signatureMac=javax.crypto.Mac.getInstance("HmacSHA1");signatureMac.init(new javax.crypto.spec.SecretKeySpec("mock-twilio-token".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA1"));
   String validSignature=Base64.getEncoder().encodeToString(signatureMac.doFinal(signatureInput.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   check(CallbackSignatures.twilio(canonical,signatureParams,validSignature,"mock-twilio-token"),"Complete Twilio parameter signature rejected");
   for(String covered:List.of("AccountSid","MessageSid","MessageStatus")){
    var altered=new TreeMap<>(signatureParams);altered.put(covered,altered.get(covered)+"altered");
    check(!CallbackSignatures.twilio(canonical,altered,validSignature,"mock-twilio-token"),"Twilio signature ignored "+covered);
   }
   var email=new EmailAdapter("test",ProviderHttp.Mode.MOCK,"127.0.0.1",mail.getMappedPort(1025),null,null,"synthetic@example.invalid");
   email.send("recipient@example.invalid","Synthetic contract","Synthetic fixture body");
   var inbox=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+mail.getMappedPort(8025)+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString());check(EventCodec.JSON.readTree(inbox.body()).path("total").asInt()==1,"SMTP transport did not deliver to Mailpit");
   rejected(()->new EmailAdapter("prod",ProviderHttp.Mode.MOCK,"mailpit",1025,null,null,"synthetic@example.invalid"));
   pay.getDockerClient().restartContainerCmd(pay.getContainerId()).exec();
   String restartedBase="http://127.0.0.1:"+pay.getDockerClient().inspectContainerCmd(pay.getContainerId()).exec().getNetworkSettings().getPorts().getBindings().get(new ExposedPort(8080))[0].getHostPortSpec();
   boolean restarted=false;
   for(int n=0;n<30;n++){try{check(admin(restartedBase,"state",null).path("operations").has("order-1"),"Restart lost persisted provider state");restarted=true;break;}catch(java.io.IOException starting){Thread.sleep(200);}}
   check(restarted,"Mock did not recover within restart deadline");
   var restartedAdapter=new PaystackAdapter("test",ProviderHttp.Mode.MOCK,restartedBase,"sk_mock_nigson");
   for(int n=0;n<130;n++)restartedAdapter.verify("order-1",123456);
   check(admin(restartedBase,"state",null).path("journal").size()==128,"Journal not bounded");
   admin(restartedBase,"reset",Map.of());check(admin(restartedBase,"state",null).path("operations").isEmpty(),"Reset failed");
   System.out.println("PASS: real HTTP Paystack/FedEx/SMS/WhatsApp success, failures, lost responses, reconciliation, state progression, redacted journal, reset and production isolation");
   }
  }
 }
}
