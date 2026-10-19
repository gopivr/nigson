package com.nigson.acceptance.pricing;

import java.net.*;
import java.net.http.*;
import java.util.*;
import com.nigson.commerce.CommerceApplication;
import com.nigson.commerce.authorization.EntitlementValues;
import com.nigson.shared.events.EventCodec;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;

public final class DistributorEntitlementHttpAcceptance {
 private DistributorEntitlementHttpAcceptance(){}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base,com.nigson.acceptance.provider.JobHttpAcceptance.Fixture identity)throws Exception{
  if(System.getenv("NIGSON_TEST_AUTHO")==null)return;
  try(var fixture=new PricingFixture(app.getBean(JdbcTemplate.class),app.getBean(PlatformTransactionManager.class))){
   UUID command=UUID.randomUUID(),application=UUID.randomUUID();var request=EventCodec.JSON.valueToTree(Map.of("commandId",command,"applicationId",application,"partyId",fixture.customer.party(),"organizationName","Synthetic API-117 organization","countryCode","NG","businessReference","HTTP-"+application,"decision","ACTIVATE","marketId",fixture.market));String body=EventCodec.JSON.writeValueAsString(request),uri=base+"/internal/v1/distributor-entitlements",token=identity.workload("entitlement.write","nigson-experience","nigson-commerce");
   assertThat(send(http,uri,identity.workload("logistics.read","nigson-integration","nigson-commerce"),"0", "wrong",body).statusCode()).isEqualTo(401);
   var created=send(http,uri,token,"0","activate-"+command,body);assertThat(created.statusCode()).as("API-117 activation: %s",created.body()).isEqualTo(200);assertThat(created.headers().firstValue("ETag")).hasValue("\"0\"");assertThat(created.headers().firstValue("Cache-Control")).hasValue("no-store");var ack=EventCodec.JSON.readTree(created.body());assertThat(ack.propertyNames()).containsExactlyInAnyOrder("commandId","organizationId","state","revision");assertThat(ack.path("state").asString()).isEqualTo("ACTIVE");assertThat(send(http,uri,token,"0","activate-"+command,body).body()).isEqualTo(created.body());
   var suspension=(tools.jackson.databind.node.ObjectNode)request.deepCopy();UUID suspendCommand=UUID.randomUUID();suspension.put("commandId",suspendCommand.toString());suspension.put("decision","SUSPEND");String suspendBody=EventCodec.JSON.writeValueAsString(suspension);var suspended=send(http,uri,token,"0","suspend-"+suspendCommand,suspendBody);assertThat(suspended.statusCode()).as("API-117 suspension: %s",suspended.body()).isEqualTo(200);assertThat(suspended.headers().firstValue("ETag")).hasValue("\"1\"");assertThat(EventCodec.JSON.readTree(suspended.body()).path("state").asString()).isEqualTo("SUSPENDED");assertThat(send(http,uri,token,"0","suspend-"+suspendCommand,suspendBody).body()).isEqualTo(suspended.body());
   var stale=(tools.jackson.databind.node.ObjectNode)request.deepCopy();stale.put("commandId",UUID.randomUUID().toString());assertThat(send(http,uri,token,"4","stale-"+UUID.randomUUID(),EventCodec.JSON.writeValueAsString(stale)).statusCode()).isEqualTo(412);
   var extra=(tools.jackson.databind.node.ObjectNode)request.deepCopy();extra.put("staffRole","ADMIN");assertThat(send(http,uri,token,"0","bad-"+UUID.randomUUID(),EventCodec.JSON.writeValueAsString(extra)).statusCode()).isEqualTo(400);
   UUID organization=UUID.fromString(ack.path("organizationId").asString());fixture.organizations.add(organization);assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commerce.outbox_event WHERE aggregate_id=? AND event_type='distributor.entitlement.changed.v1'",Integer.class,organization)).isEqualTo(2);var metrics=app.getBean(io.micrometer.core.instrument.MeterRegistry.class);assertThat(metrics.get("nigson.authorization.entitlement").tag("operation","ACTIVATE").counter().count()).isEqualTo(1);assertThat(metrics.get("nigson.authorization.entitlement").tag("operation","SUSPEND").counter().count()).isEqualTo(1);fixture.jdbc.update("DELETE FROM commerce.outbox_event WHERE aggregate_id=?",organization);
  }
 }
 private static HttpResponse<String> send(HttpClient http,String uri,String token,String version,String key,String body)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token).header("If-Match","\""+version+"\"").header("Idempotency-Key",key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
}
