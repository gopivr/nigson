package com.nigson.acceptance.carrier;
import java.util.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.acceptance.fulfillment.ShipmentFixture;
import com.nigson.acceptance.files.StorageFixture;
import com.nigson.acceptance.provider.JobHttpAcceptance;
import com.nigson.shared.events.EventCodec;
import com.nigson.commerce.fulfillment.*;
import static org.assertj.core.api.Assertions.*;
/** Actual source private identity and TLS/SigV4 provenance through production conditional registration. */
public final class CarrierResultHttpAcceptance {
 private CarrierResultHttpAcceptance(){}
 public static void configure(Map<String,Object> properties)throws Exception{String path=System.getenv("NIGSON_TEST_CARRIER_STORAGE_FIXTURE");if(path==null)return;var storage=EventCodec.JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(path)));properties.put("NIGSON_CARRIER_RESULTS_ENABLED","true");properties.put("NIGSON_FILES_ENABLED","true");properties.put("NIGSON_S3_LOCAL_ENDPOINT",storage.path("endpoint").asString());properties.put("NIGSON_S3_LOCAL_CA",storage.path("ca").asString());properties.put("NIGSON_AWS_REGION",storage.path("region").asString());properties.put("NIGSON_S3_QUARANTINE_BUCKET",storage.path("buckets").get(0).asString());properties.put("NIGSON_S3_PUBLIC_BUCKET",storage.path("buckets").get(1).asString());properties.put("NIGSON_S3_PRIVATE_BUCKET",storage.path("buckets").get(2).asString());}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base,JobHttpAcceptance.Fixture identity)throws Exception{String path=System.getenv("NIGSON_TEST_CARRIER_STORAGE_FIXTURE");if(path==null)return;try(var storage=new StorageFixture(path);var f=new ShipmentFixture(app.getBean(JdbcTemplate.class),app.getBean(PlatformTransactionManager.class))){CarrierContextPersistenceTest.freeze(f);var shipment=f.create("5","FEDEX",null,"http-result");String booking=f.inventory.jdbc.queryForObject("SELECT booking_request_id::text FROM commerce.shipment WHERE id=?",String.class,shipment.id());var input=CarrierResultPersistenceTest.putLabel(storage,shipment.id(),UUID.randomUUID(),booking);String uri=base+"/internal/v1/shipments/"+shipment.id()+"/provider-result",token=identity.workload("logistics.write","nigson-integration","nigson-commerce");var wire=(tools.jackson.databind.node.ObjectNode)EventCodec.JSON.valueToTree(input);wire.remove("trackingUrl");String body=EventCodec.JSON.writeValueAsString(wire);var denied=send(http,uri,identity.workload("logistics.read","nigson-integration","nigson-commerce"),body);assertThat(denied.statusCode()).isEqualTo(401);var result=send(http,uri,token,body);assertThat(result.statusCode()).as("private provider result: %s",result.body()).isEqualTo(200);assertThat(result.headers().firstValue("Cache-Control")).hasValue("no-store");var dto=EventCodec.JSON.readTree(result.body());assertThat(dto.path("status").asString()).isEqualTo("READY");assertThat(dto.propertyNames()).containsExactlyInAnyOrder("id","orderId","status","lines","trackingUrl","version");assertThat(dto.has("labelObjectKey")).isFalse();int heads=storage.control(null).path("heads").asInt();assertThat(send(http,uri,token,body).body()).isEqualTo(result.body());assertThat(storage.control(null).path("heads").asInt()).isEqualTo(heads);assertThat(send(http,uri+"?account=unsafe",token,body).statusCode()).isEqualTo(400);var unknown=wire.deepCopy();unknown.put("deliveryFee","1.00");assertThat(send(http,uri,token,EventCodec.JSON.writeValueAsString(unknown)).statusCode()).isEqualTo(400);assertThat(f.value("on_hand")).isEqualByComparingTo("10");assertThat(f.value("allocated")).isEqualByComparingTo("5");}
 }
 private static HttpResponse<String> send(HttpClient http,String uri,String token,String body)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());}
}
