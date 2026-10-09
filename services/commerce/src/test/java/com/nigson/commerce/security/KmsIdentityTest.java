package com.nigson.commerce.security;
import com.nigson.shared.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
class KmsIdentityTest {
 static final String ARN="arn:aws:kms:eu-west-1:000000000000:key/12345678-1234-1234-1234-123456789012";
 @Test void realSdkHttpDigestSignatureContextFailureAndTimeoutAreBounded()throws Exception{
  var keys=SecurityProtocolTest.pair();var accepted=new AtomicInteger();var mode=new AtomicInteger();var json=new tools.jackson.databind.json.JsonMapper();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
  server.createContext("/",exchange->{try{byte[] body=exchange.getRequestBody().readNBytes(20000);var request=json.readTree(body);assertThat(request.get("KeyId").asString()).isEqualTo(ARN);assertThat(exchange.getRequestHeaders().getFirst("Authorization")).contains("Credential=mock-local-kms/");accepted.incrementAndGet();
   if(mode.get()==1){exchange.sendResponseHeaders(500,-1);exchange.close();return;}
   Map<String,Object> response;
   if(exchange.getRequestHeaders().getFirst("X-Amz-Target").endsWith(".Sign")){assertThat(request.get("MessageType").asString()).isEqualTo("DIGEST");assertThat(request.get("SigningAlgorithm").asString()).isEqualTo("ECDSA_SHA_256");byte[] digest=Base64.getDecoder().decode(request.get("Message").asString());assertThat(digest).hasSize(32);var signer=Signature.getInstance("NONEwithECDSA");signer.initSign(keys.getPrivate());signer.update(digest);response=Map.of("KeyId",mode.get()==2?ARN+"-wrong":ARN,"SigningAlgorithm","ECDSA_SHA_256","Signature",Base64.getEncoder().encodeToString(signer.sign()));}
   else {assertThat(request.get("EncryptionContext").get("purpose").asString()).isEqualTo("nigson-commerce-session");assertThat(request.get("EncryptionContext").get("revision").asString()).isEqualTo("v1");response=Map.of("KeyId",ARN,"Plaintext",Base64.getEncoder().encodeToString(new byte[32]));}
   if(mode.get()==3)Thread.sleep(1000);byte[] bytes=json.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/x-amz-json-1.1");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  }catch(Exception e){exchange.close();}});server.start();
  try(var kms=KmsClient.builder().region(Region.EU_WEST_1).endpointOverride(URI.create("http://127.0.0.1:"+server.getAddress().getPort())).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("mock-local-kms","mock-local-kms-secret"))).overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofMillis(700)).apiCallAttemptTimeout(Duration.ofMillis(500)).retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build()).build()){
   var b=SecurityProtocolTest.binding();var a=SecurityProtocolTest.actor();var signer=new IdentityKeys(kms,ARN,"active",(ECPublicKey)keys.getPublic(),SecurityProtocolTest.CLOCK);String token=signer.sign(a,b);assertThat(ActorAssertion.verify(token,b,Map.of("active",(ECPublicKey)keys.getPublic()),Set.of(SecurityProtocolTest.ISSUER),SecurityProtocolTest.CLOCK).party()).isEqualTo(a.party());assertThat(IdentityKeys.unwrap(kms,ARN,Base64.getEncoder().encodeToString(new byte[32]),"v1")).hasSize(32);
   for(int failure:List.of(1,2,3)){mode.set(failure);int before=accepted.get();assertThatThrownBy(()->signer.sign(a,b)).hasMessage("ACTOR_SIGNING_UNAVAILABLE");assertThat(accepted.get()-before).isEqualTo(1);}
   TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->signer.sign(a,b)).hasMessage("IDENTITY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
  }finally{server.stop(0);}
 }
}
