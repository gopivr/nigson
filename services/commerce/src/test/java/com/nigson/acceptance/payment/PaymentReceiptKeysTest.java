package com.nigson.acceptance.payment;
import java.util.*;
import java.net.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.commerce.payment.PaymentReceiptKeys;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import static org.assertj.core.api.Assertions.*;
class PaymentReceiptKeysTest {
 @Test void actualSdkHttpBindsReceiptPurposeRotationAndRejectsWrongKeysAndLockedHttp()throws Exception{
  String arn="arn:aws:kms:eu-west-1:000000000000:key/12345678-1234-1234-1234-123456789012",wrapped=Base64.getEncoder().encodeToString(new byte[32]);var json=new tools.jackson.databind.json.JsonMapper();var calls=new AtomicInteger();var mode=new AtomicInteger();var failures=new java.util.concurrent.CopyOnWriteArrayList<Throwable>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);
  server.createContext("/",exchange->{try{var request=json.readTree(exchange.getRequestBody().readNBytes(20000));calls.incrementAndGet();assertThat(exchange.getRequestHeaders().getFirst("X-Amz-Target")).endsWith(".Decrypt");assertThat(exchange.getRequestHeaders().getFirst("Authorization")).contains("Credential=mock-local-kms/");assertThat(request.path("KeyId").asString()).isEqualTo(arn);assertThat(request.path("EncryptionContext").path("purpose").asString()).isEqualTo("nigson-commerce-payment-receipts");assertThat(request.path("EncryptionContext").path("revision").asString()).isIn("r1","r0");if(mode.get()==3){exchange.sendResponseHeaders(500,-1);return;}if(mode.get()==4)Thread.sleep(1000);byte[] bytes=json.writeValueAsBytes(Map.of("KeyId",mode.get()==1?arn+"-wrong":arn,"Plaintext",Base64.getEncoder().encodeToString(new byte[mode.get()==2?31:32])));exchange.getResponseHeaders().set("Content-Type","application/x-amz-json-1.1");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}catch(java.io.IOException closed){}catch(Throwable failure){failures.add(failure);}finally{exchange.close();}});server.start();
  try(var kms=KmsClient.builder().region(Region.EU_WEST_1).endpointOverride(URI.create("http://127.0.0.1:"+server.getAddress().getPort())).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("mock-local-kms","mock-local-kms-secret"))).overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofMillis(700)).apiCallAttemptTimeout(Duration.ofMillis(500)).retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build()).build()){
   assertThat(PaymentReceiptKeys.load(kms,"test","r1",arn,wrapped,"",EventString.previous(wrapped))).isNotNull();assertThat(calls.get()).isEqualTo(2);for(int fault:List.of(1,2,3,4)){mode.set(fault);int before=calls.get();assertThatThrownBy(()->PaymentReceiptKeys.load(kms,"test","r1",arn,wrapped,"","{}")).hasMessage("PAYMENT_RECEIPT_KEY_UNAVAILABLE");assertThat(calls.get()-before).isEqualTo(1);}int before=calls.get();TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->PaymentReceiptKeys.load(kms,"test","r1",arn,wrapped,"","{}")).hasMessage("PAYMENT_KEY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}assertThat(calls.get()).isEqualTo(before);assertThat(failures).isEmpty();
  }finally{server.stop(0);executor.shutdownNow();}
 }
 private static final class EventString {static String previous(String wrapped){return "{\"r0\":\""+wrapped+"\"}";}}
}
