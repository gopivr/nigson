package com.nigson.acceptance.carrier;
import java.net.*;
import java.net.http.*;
import java.time.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.acceptance.fulfillment.ShipmentFixture;
import com.nigson.acceptance.provider.JobHttpAcceptance;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;
/** Actual Keycloak JWT/JWKS private ingress and owned immutable logistics HTTP. */
public final class CarrierContextHttpAcceptance {
 private CarrierContextHttpAcceptance(){}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base,JobHttpAcceptance.Fixture issuer)throws Exception{
  if(System.getenv("NIGSON_TEST_CARRIER")==null)return;
  try(var f=new ShipmentFixture(app.getBean(JdbcTemplate.class),app.getBean(PlatformTransactionManager.class))){CarrierContextPersistenceTest.freeze(f);var shipment=f.create("5","FEDEX",null,"http-context");String uri=base+"/internal/v1/shipments/"+shipment.id()+"/provider-context";String token=issuer.workload("logistics.read","nigson-integration","nigson-commerce");
   var response=send(http,uri,token);assertThat(response.statusCode()).as("private context: %s",response.body()).isEqualTo(200);assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");var body=EventCodec.JSON.readTree(response.body());assertThat(body.propertyNames()).containsExactlyInAnyOrder("shipmentId","orderId","address","shipper","serviceCode","packages","bookingReference","mode","accountHash");assertThat(body.path("shipper").path("recipient").asString()).isEqualTo("Synthetic frozen origin");assertThat(body.path("packages")).hasSize(1);assertThat(body.path("address").propertyNames()).doesNotContain("id","version","organizationId");
   assertThat(send(http,uri,issuer.workload("logistics.write","nigson-integration","nigson-commerce")).statusCode()).isEqualTo(401);assertThat(send(http,uri,issuer.workload("logistics.read","nigson-experience","nigson-commerce")).statusCode()).isEqualTo(401);assertThat(send(http,uri+"?account=unsafe",token).statusCode()).isEqualTo(400);
   var withBody=http.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token).method("GET",HttpRequest.BodyPublishers.ofString("{}")).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());assertThat(withBody.statusCode()).isEqualTo(400);f.inventory.jdbc.update("UPDATE commerce.shipment SET status='READY' WHERE id=?",shipment.id());assertThat(send(http,uri,token).statusCode()).isEqualTo(409);f.inventory.jdbc.update("UPDATE commerce.shipment SET status='CREATED' WHERE id=?",shipment.id());f.inventory.jdbc.update("UPDATE commerce.payment_attempt SET verified_at=NULL WHERE order_id=?",f.order);assertThat(send(http,uri,token).statusCode()).isEqualTo(422);assertThat(f.value("on_hand")).isEqualByComparingTo("10");assertThat(f.value("allocated")).isEqualByComparingTo("5");
  }
 }
 private static HttpResponse<String> send(HttpClient http,String uri,String token)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token).GET().timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());}
}
