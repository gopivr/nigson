package com.nigson.acceptance.notification;
import com.nigson.acceptance.provider.JobHttpAcceptance;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
/** Real BFF sessions and private JWT/KMS protocols against stateful local worker fixtures. */
public final class NotificationHttpAcceptance {
 private NotificationHttpAcceptance(){}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base,JobHttpAcceptance.Fixture fixture)throws Exception{
  if(System.getenv("NIGSON_TEST_NOTIFICATION")==null)return;
  var jdbc=app.getBean(JdbcTemplate.class);var sessions=app.getBean(JdbcSessionTrust.class);UUID admin=UUID.randomUUID(),customer=UUID.randomUUID(),stale=UUID.randomUUID();var ids=List.of(admin,customer,stale);var cookies=new HashMap<UUID,String>();var csrf=new HashMap<UUID,String>();String root=base+"/api/v1/staff/notification-templates",body=EventCodec.JSON.writeValueAsString(NotificationValuesTest.template());
  try(var orders=new OrderFixture(jdbc)){
   UUID order=orders.order(),job=UUID.randomUUID();String read=base+"/internal/v1/notification-context/"+order,delivery=base+"/internal/v1/notification-deliveries";
   var context=send(http,privateRequest(read,fixture.workload("notification.read","nigson-integration","nigson-commerce")).GET());assertThat(context.statusCode()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(context.body()).propertyNames()).containsExactlyInAnyOrder("orderNumber","recipient","total","currency","trackingUrl");assertThat(context.body()).doesNotContain("address_snapshot","commercial_snapshot","identity_subject");assertThat(context.headers().firstValue("Cache-Control")).hasValue("no-store");
   assertThat(send(http,privateRequest(read,fixture.workload("notification.write","nigson-integration","nigson-commerce")).GET()).statusCode()).isEqualTo(401);
   assertThat(send(http,privateRequest(read,fixture.workload("notification.read","nigson-commerce","nigson-commerce")).GET()).statusCode()).isEqualTo(401);
   assertThat(send(http,privateRequest(read+"?include=profile",fixture.workload("notification.read","nigson-integration","nigson-commerce")).GET()).statusCode()).isEqualTo(400);
   assertThat(send(http,privateRequest(read,fixture.workload("notification.read","nigson-integration","nigson-commerce")).header("X-Nigson-Actor","synthetic-browser-assertion").GET()).statusCode()).isEqualTo(401);
   String receipt=EventCodec.JSON.writeValueAsString(Map.of("jobId",job,"resourceId",order,"state","SENT","providerReference","synthetic-reference"));String writeToken=fixture.workload("notification.write","nigson-integration","nigson-commerce");
   assertThat(send(http,privateRequest(delivery,writeToken).header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(receipt.replace("SENT","DELIVERED")))).statusCode()).isEqualTo(400);
   for(int i=0;i<3;i++){var response=send(http,privateRequest(delivery,writeToken).header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(receipt)));assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isEqualTo("{\"acknowledged\":true}");}
   assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.idempotency_record WHERE operation='API-133-OUTCOME-SENT' AND request_key=?",Integer.class,job.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT status FROM commerce.sales_order WHERE id=?",String.class,order)).isEqualTo("PENDING");
   for(var id:ids){jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic template admin')",id,fixture.issuer,id.toString(),id+"@example.invalid");if(!id.equals(customer))jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'ADMIN',?)",UUID.randomUUID(),id,id);var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());String token=JdbcSessionTrust.opaque();csrf.put(id,token);String session=sessions.authenticate(flow.sessionId(),new SessionRecord(1,id,fixture.issuer,id.toString(),Instant.now(),Instant.now().minusSeconds(id.equals(stale)?901:0),"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),token,SessionRecord.TokenState.READY));cookies.put(id,"__Host-nigson-session="+session);}
   assertThat(send(http,mutation(root,cookies.get(admin),"wrong","csrf",body)).statusCode()).isEqualTo(401);
   assertThat(send(http,mutation(root,cookies.get(customer),csrf.get(customer),"role",body)).statusCode()).isEqualTo(403);
   assertThat(send(http,mutation(root,cookies.get(stale),csrf.get(stale),"mfa",body)).statusCode()).isEqualTo(403);
   assertThat(send(http,mutation(root,cookies.get(admin),csrf.get(admin),"stale-version",body).header("If-Match","\"3\"")).statusCode()).isEqualTo(412);
   String key=UUID.randomUUID().toString();fixture.templateAfterAccept=true;var uncertain=send(http,mutation(root,cookies.get(admin),csrf.get(admin),key,body));assertThat(uncertain.statusCode()).isEqualTo(503);assertThat(uncertain.body()).contains("PROVIDER_OUTCOME_UNKNOWN");assertThat(fixture.templateEffects.get()).isEqualTo(1);
   var accepted=send(http,mutation(root,cookies.get(admin),csrf.get(admin),key,body));assertThat(accepted.statusCode()).isEqualTo(200);assertThat(accepted.body()).isEqualTo("{\"code\":\"ORDER_PAID\",\"revision\":1}");assertThat(accepted.headers().firstValue("ETag")).hasValue("\"1\"");int signs=fixture.signs.get();for(int i=0;i<3;i++)assertThat(send(http,mutation(root,cookies.get(admin),csrf.get(admin),key,body)).body()).isEqualTo(accepted.body());assertThat(fixture.signs.get()).isEqualTo(signs);assertThat(fixture.templateEffects.get()).isEqualTo(1);
   assertThat(send(http,mutation(root,cookies.get(admin),csrf.get(admin),key,body.replace("paid","changed"))).statusCode()).isEqualTo(409);
   var immutable=send(http,mutation(root,cookies.get(admin),csrf.get(admin),"immutable-revision",body.replace("paid","changed")));assertThat(immutable.statusCode()).isEqualTo(422);assertThat(immutable.body()).doesNotContain("protected template payload");
   var metrics=app.getBean(io.micrometer.core.instrument.MeterRegistry.class);assertThat(metrics.find("nigson.notification.outcomes").tag("state","SENT").counter().count()).isEqualTo(1);assertThat(metrics.find("nigson.notification.templates").tag("outcome","ACKNOWLEDGED").counter().count()).isEqualTo(1);assertThat(metrics.find("nigson.notification.templates").tag("outcome","UNKNOWN").counter().count()).isEqualTo(1);assertThat(metrics.find("nigson.provider.commands").tag("outcome","ACKNOWLEDGED").counter().count()).isEqualTo(1);
   jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",admin);assertThat(send(http,mutation(root,cookies.get(admin),csrf.get(admin),key,body)).statusCode()).isEqualTo(403);
  }finally{for(var id:ids){String cookie=cookies.get(id);if(cookie!=null)sessions.logout(cookie.substring(cookie.indexOf('=')+1));jdbc.update("DELETE FROM commerce.idempotency_record WHERE principal_key=?",id.toString());jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}}
 }
 static HttpRequest.Builder privateRequest(String uri,String token){return HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token).header("X-Correlation-ID",UUID.randomUUID().toString());}
 static HttpRequest.Builder mutation(String uri,String cookie,String csrf,String key,String body){return HttpRequest.newBuilder(URI.create(uri)).header("Cookie",cookie).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/json").header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body));}
 static HttpResponse<String> send(HttpClient http,HttpRequest.Builder request)throws Exception{return http.send(request.timeout(Duration.ofSeconds(15)).build(),HttpResponse.BodyHandlers.ofString());}
}
