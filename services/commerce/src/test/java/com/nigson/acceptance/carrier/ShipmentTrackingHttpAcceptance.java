package com.nigson.acceptance.carrier;
import java.util.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.acceptance.fulfillment.ShipmentFixture;
import com.nigson.acceptance.provider.JobHttpAcceptance;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;
/** Actual BFF session/authority, deny-by-default route and safe owned customer projection. */
public final class ShipmentTrackingHttpAcceptance {
 private ShipmentTrackingHttpAcceptance(){}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base,JobHttpAcceptance.Fixture upstream)throws Exception{if(System.getenv("NIGSON_TEST_CARRIER")==null)return;var sessions=app.getBean(JdbcSessionTrust.class);String session=null;try(var f=new ShipmentFixture(app.getBean(JdbcTemplate.class),app.getBean(PlatformTransactionManager.class))){UUID customer=f.inventory.orders.party;f.inventory.jdbc.update("UPDATE commerce.party SET identity_issuer=? WHERE id=?",upstream.issuer,customer);var shipment=f.create("5","FEDEX",null,"http-tracking");f.inventory.jdbc.update("INSERT INTO commerce.shipment_event(id,created_by,shipment_id,source,event_key,status,occurred_at,note) VALUES (?,'test',?,'FEDEX',?,'UNKNOWN',now(),'Synthetic private tracking diagnostic')",UUID.randomUUID(),shipment.id(),UUID.randomUUID().toString());var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());session=sessions.authenticate(flow.sessionId(),new SessionRecord(1,customer,upstream.issuer,customer.toString(),Instant.now(),null,"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),JdbcSessionTrust.opaque(),SessionRecord.TokenState.READY));String uri=base+"/api/v1/orders/"+f.order+"/tracking",cookie="__Host-nigson-session="+session;
  assertThat(http.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);var result=send(http,uri,cookie);assertThat(result.statusCode()).as("tracking: %s",result.body()).isEqualTo(200);assertThat(result.headers().firstValue("Cache-Control")).hasValue("no-store");var dto=EventCodec.JSON.readTree(result.body());assertThat(dto.propertyNames()).containsExactlyInAnyOrder("orderId","shipments","events");assertThat(dto.path("shipments")).hasSize(1);assertThat(dto.path("shipments").path(0).path("status").asString()).isEqualTo("CREATED");assertThat(result.body()).doesNotContain("Synthetic private tracking diagnostic","labelObjectKey","packageSnapshot","providerSnapshot");assertThat(send(http,uri+"?provider=FEDEX",cookie).statusCode()).isEqualTo(400);var body=http.send(HttpRequest.newBuilder(URI.create(uri)).header("Cookie",cookie).method("GET",HttpRequest.BodyPublishers.ofString("{}")).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());assertThat(body.statusCode()).isEqualTo(400);assertThat(send(http,base+"/api/v1/orders/"+UUID.randomUUID()+"/tracking",cookie).statusCode()).isEqualTo(404);f.inventory.jdbc.update("UPDATE commerce.party SET status='SUSPENDED' WHERE id=?",customer);assertThat(send(http,uri,cookie).statusCode()).isEqualTo(401);assertThat(f.value("on_hand")).isEqualByComparingTo("10");assertThat(f.value("allocated")).isEqualByComparingTo("5");
 }finally{if(session!=null)sessions.logout(session);}}
 private static HttpResponse<String> send(HttpClient http,String uri,String cookie)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(uri)).header("Cookie",cookie).GET().timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());}
}
