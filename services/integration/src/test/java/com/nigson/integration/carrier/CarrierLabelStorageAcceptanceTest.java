package com.nigson.integration.carrier;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.time.*;
import java.util.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import static org.assertj.core.api.Assertions.*;

class CarrierLabelStorageAcceptanceTest {
 private JsonNode config;private HttpClient http;private CarrierLabelStorage storage;private S3Client client;private CarrierLabelCodec.Label label;private final UUID shipment=UUID.randomUUID(),booking=UUID.randomUUID();
 @BeforeEach void setup()throws Exception{String path=System.getenv("NIGSON_TEST_CARRIER_STORAGE_FIXTURE");Assumptions.assumeTrue(path!=null,"actual isolated TLS/SigV4 fixture required");config=EventCodec.JSON.readTree(Files.readString(Path.of(path)));var env=new MockEnvironment().withProperty("APP_ENV","test").withProperty("NIGSON_SECURITY_ENABLED","true").withProperty("NIGSON_AWS_REGION",config.path("region").asString()).withProperty("NIGSON_S3_LOCAL_ENDPOINT",config.path("endpoint").asString()).withProperty("NIGSON_S3_LOCAL_CA",config.path("ca").asString()).withProperty("NIGSON_S3_PRIVATE_BUCKET",config.path("buckets").path(2).asString());var factory=new CarrierLabelConfiguration();client=factory.carrierLabelClient(env);storage=factory.carrierLabelStorage(client,env);var trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);try(var input=Files.newInputStream(Path.of(config.path("ca").asString()))){trust.setCertificateEntry("mock",CertificateFactory.getInstance("X.509").generateCertificate(input));}var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(trust);var tls=SSLContext.getInstance("TLS");tls.init(null,managers.getTrustManagers(),null);http=HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(2)).build();label=CarrierLabelCodec.decode(CarrierLabelCodecTest.output(CarrierLabelCodecTest.pdf(1,false,false)),"123456789012",1);}
 @AfterEach void close(){if(client!=null)client.close();}
 private JsonNode control(String fault)throws Exception{var body=EventCodec.JSON.createObjectNode();if(fault==null)body.putNull("fault");else body.put("fault",fault);var request=HttpRequest.newBuilder(URI.create(config.path("endpoint").asString()+"/__test")).header("X-Test-Control",config.path("admin").asString()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(EventCodec.JSON.writeValueAsBytes(body))).timeout(Duration.ofSeconds(5)).build();var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());assertThat(response.statusCode()).isEqualTo(200);return EventCodec.JSON.readTree(response.body());}
 @Test void immutablePrivatePutAndHeadRecoverExactVersionWithoutOverwrite()throws Exception{int before=control(null).path("puts").asInt();var first=storage.store(shipment,booking,label);var repeated=storage.store(shipment,booking,label);assertThat(first).isEqualTo(repeated);assertThat(first.key()).isEqualTo("test/commerce/shipping_label/"+shipment+"/1/pdf");assertThat(first.checksum()).isEqualTo(label.checksum());assertThat(first.version()).isNotBlank();assertThat(control(null).path("puts").asInt()).isEqualTo(before+1);assertThat(first.toString()).isEqualTo("StoredCarrierLabel[protected]");assertThatThrownBy(()->storage.store(shipment,UUID.randomUUID(),label)).isInstanceOf(Problem.Failure.class);assertThat(control(null).path("puts").asInt()).isEqualTo(before+1);}
 @Test void responseLostAfterConditionalPutIsResolvedByTrustedHead()throws Exception{int before=control("PUT_AFTER_ACCEPT").path("puts").asInt();var result=storage.store(shipment,booking,label);assertThat(result.checksum()).isEqualTo(label.checksum());assertThat(control(null).path("puts").asInt()).isEqualTo(before+1);}
 @Test void unacceptedWriteRetainsUnknownAndMalformedHeadCannotBeTrusted()throws Exception{control("PUT_BEFORE_ACCEPT");assertThatThrownBy(()->storage.store(shipment,booking,label)).isInstanceOfSatisfying(Problem.Failure.class,f->assertThat(f.code).isEqualTo(ProblemCode.PROVIDER_OUTCOME_UNKNOWN));storage.store(shipment,booking,label);control("INVALID_HEAD_CHECKSUM");assertThatThrownBy(()->storage.store(shipment,booking,label)).isInstanceOf(Problem.Failure.class);control(null);}
 @Test void transactionAndProductionFallbackAreRejectedBeforeStorageHttp(){TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->storage.store(shipment,booking,label)).hasMessage("PROVIDER_CALL_INSIDE_TRANSACTION");}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}var env=new MockEnvironment().withProperty("APP_ENV","prod").withProperty("NIGSON_SECURITY_ENABLED","true").withProperty("NIGSON_AWS_REGION","us-east-1").withProperty("NIGSON_S3_LOCAL_ENDPOINT",config.path("endpoint").asString());assertThatThrownBy(()->new CarrierLabelConfiguration().carrierLabelClient(env)).isInstanceOf(IllegalArgumentException.class);}
}
