package com.nigson.acceptance.party;
import com.nigson.commerce.party.*;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;
import static org.assertj.core.api.Assertions.*;
public final class PartyHttpAcceptance {
 private PartyHttpAcceptance(){}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base)throws Exception{
  var jdbc=app.getBean(JdbcTemplate.class);var service=app.getBean(PartyService.class);var sessions=app.getBean(JdbcSessionTrust.class);
  String issuer="http://127.0.0.1:9/realms/offline";UUID market=UUID.randomUUID(),organization=UUID.randomUUID();List<UUID> ids=new ArrayList<>();Map<UUID,String> cookies=new HashMap<>(),tokens=new HashMap<>();
  try{
   for(int i=0;i<2;i++){String subject=UUID.randomUUID().toString();UUID id=service.provision(new PartyValues.VerifiedIdentity(issuer,subject,subject+"@example.invalid",true,"Synthetic HTTP customer"));ids.add(id);var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());String token=JdbcSessionTrust.opaque();tokens.put(id,token);cookies.put(id,"__Host-nigson-session="+sessions.authenticate(flow.sessionId(),new SessionRecord(1,id,issuer,subject,Instant.now(),null,"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),token,SessionRecord.TokenState.READY)));}
   UUID id=ids.getFirst();String cookie=cookies.get(id),csrf=tokens.get(id),me=base+"/api/v1/me",addresses=me+"/addresses";
   jdbc.update("INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES (?,'test','test','NGN',2) ON CONFLICT(code) DO NOTHING",UUID.randomUUID());
   jdbc.update("INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled) VALUES (?,'test','test',?,'Synthetic HTTP policy','NGN',true)",market,"HTTP_"+market.toString().replace("-","").substring(0,20));
   jdbc.update("INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules) VALUES (?,'test','test',?,'NG',?::jsonb)",UUID.randomUUID(),market,PartyPersistenceTest.RULES);
   jdbc.update("INSERT INTO commerce.organization(id,created_by,updated_by,name,business_reference,country_code,status) VALUES (?,'test','test','Synthetic HTTP organization',?,'NG','ACTIVE')",organization,"HTTP-"+organization);
   jdbc.update("INSERT INTO commerce.organization_membership(id,created_by,updated_by,organization_id,party_id,role,status) VALUES (?,'test','test',?,?, 'OWNER','ACTIVE')",UUID.randomUUID(),organization,ids.getFirst());
   jdbc.update("INSERT INTO commerce.account_entitlement(id,created_by,updated_by,organization_id,entitlement,status,source_application_id,approval_command_id) VALUES (?,'test','test',?,'SYNTHETIC_DISTRIBUTOR_BUY','ACTIVE',?,?)",UUID.randomUUID(),organization,UUID.randomUUID(),UUID.randomUUID());
   assertThat(send(http,HttpRequest.newBuilder(URI.create(me)).GET()).statusCode()).isEqualTo(401);
   var profile=send(http,request(me,cookie).GET());assertThat(profile.statusCode()).isEqualTo(200);assertThat(profile.headers().allValues("Set-Cookie")).isEmpty();assertThat(profile.body()).doesNotContain("identitySubject","synthetic-access","synthetic-refresh");
   assertThat(EventCodec.JSON.readTree(profile.body()).properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("id","displayName","email","emailVerified","phone","locale","version");
   assertThat(send(http,request(me+"?unknown=1",cookie).GET()).statusCode()).isEqualTo(400);
   assertThat(send(http,request(me,cookie).method("GET",HttpRequest.BodyPublishers.ofString("{}"))).statusCode()).isEqualTo(400);
   String orgs=me+"/organizations";assertThat(send(http,request(orgs,null).GET()).statusCode()).isEqualTo(401);var orgList=send(http,request(orgs,cookie).GET());assertThat(orgList.statusCode()).isEqualTo(200);var orgDtos=EventCodec.JSON.readTree(orgList.body());assertThat(orgDtos.size()).isEqualTo(1);assertThat(orgDtos.get(0).propertyNames()).containsExactlyInAnyOrder("id","name","membershipRole","entitlementState");assertThat(orgDtos.get(0).path("id").asString()).isEqualTo(organization.toString());assertThat(orgDtos.get(0).path("membershipRole").asString()).isEqualTo("OWNER");assertThat(orgDtos.get(0).path("entitlementState").asString()).isEqualTo("ACTIVE");assertThat(EventCodec.JSON.readTree(send(http,request(orgs,cookies.get(ids.get(1))).GET()).body())).isEmpty();assertThat(send(http,request(orgs+"?organizationId="+organization,cookie).GET()).statusCode()).isEqualTo(400);assertThat(send(http,request(orgs,cookie).method("GET",HttpRequest.BodyPublishers.ofString("{}"))).statusCode()).isEqualTo(400);jdbc.update("UPDATE commerce.account_entitlement SET status='SUSPENDED' WHERE organization_id=?",organization);assertThat(EventCodec.JSON.readTree(send(http,request(orgs,cookie).GET()).body()).get(0).path("entitlementState").asString()).isEqualTo("SUSPENDED");jdbc.update("UPDATE commerce.organization_membership SET status='SUSPENDED' WHERE organization_id=?",organization);assertThat(EventCodec.JSON.readTree(send(http,request(orgs,cookie).GET()).body())).isEmpty();jdbc.update("UPDATE commerce.organization_membership SET status='ACTIVE' WHERE organization_id=?",organization);jdbc.update("UPDATE commerce.account_entitlement SET status='ACTIVE' WHERE organization_id=?",organization);
   String patch="{\"displayName\":\"Updated HTTP profile\",\"locale\":\"en-NG\"}",key=UUID.randomUUID().toString();
   assertThat(send(http,mutation(me,cookie,null,"0",key,"PATCH",patch)).statusCode()).isEqualTo(401);
   var changed=send(http,mutation(me,cookie,csrf,"0",key,"PATCH",patch));assertThat(changed.statusCode()).isEqualTo(200);assertThat(changed.headers().firstValue("ETag")).hasValue("\"1\"");
   assertThat(send(http,mutation(me,cookie,csrf,"0",key,"PATCH",patch)).body()).isEqualTo(changed.body());
   assertThat(send(http,mutation(me,cookie,csrf,"0","stale","PATCH",patch)).statusCode()).isEqualTo(412);
   assertThat(send(http,mutation(me,cookie,csrf,"0",key,"PATCH","{\"displayName\":\"Different\"}")).statusCode()).isEqualTo(409);
   for(String invalid:List.of("{\"phone\":123}","{\"email\":\"x@example.invalid\"}","{\"displayName\":null}","{\"phone\":null,\"phone\":\"x\"}"))assertThat(send(http,mutation(me,cookie,csrf,"1",UUID.randomUUID().toString(),"PATCH",invalid)).statusCode()).isEqualTo(400);
   String input="{\"recipient\":\"Synthetic recipient\",\"phone\":\"+2348000000000\",\"line1\":\"Synthetic street\",\"city\":\"Synthetic city\",\"countryCode\":\"NG\",\"isDefault\":true}",createKey=UUID.randomUUID().toString();
   var created=send(http,mutation(addresses,cookie,csrf,null,createKey,"POST",input));assertThat(created.statusCode()).isEqualTo(201);assertThat(send(http,mutation(addresses,cookie,csrf,null,createKey,"POST",input)).body()).isEqualTo(created.body());
   String address=base+created.headers().firstValue("Location").orElseThrow();
   var listed=send(http,request(addresses,cookie).GET());assertThat(EventCodec.JSON.readTree(listed.body()).size()).isEqualTo(1);
   assertThat(send(http,mutation(address,cookies.get(ids.get(1)),tokens.get(ids.get(1)),"0","idor","PATCH","{\"label\":\"Other owner\"}")).statusCode()).isEqualTo(404);
   var updated=send(http,mutation(address,cookie,csrf,"0","patch-address","PATCH","{\"label\":\"Updated\"}"));assertThat(updated.statusCode()).isEqualTo(200);assertThat(updated.headers().firstValue("ETag")).hasValue("\"1\"");
   assertThat(send(http,mutation(address,cookie,csrf,"1","delete-address","DELETE",null)).statusCode()).isEqualTo(204);
   assertThat(send(http,mutation(address,cookie,csrf,"1","delete-address","DELETE",null)).statusCode()).isEqualTo(204);
   assertThat(EventCodec.JSON.readTree(send(http,request(addresses,cookie).GET()).body()).size()).isZero();
  }finally{jdbc.update("DELETE FROM commerce.account_entitlement WHERE organization_id=?",organization);jdbc.update("DELETE FROM commerce.organization_membership WHERE organization_id=?",organization);jdbc.update("DELETE FROM commerce.organization WHERE id=?",organization);for(UUID id:ids){sessions.revokeSubject(id);jdbc.update("DELETE FROM commerce.address WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.customer_profile WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}jdbc.update("DELETE FROM commerce.market_destination WHERE market_id=?",market);jdbc.update("DELETE FROM commerce.market WHERE id=?",market);}
 }
 static HttpRequest.Builder request(String url,String cookie){var builder=HttpRequest.newBuilder(URI.create(url));if(cookie!=null)builder.header("Cookie",cookie);return builder;}
 static HttpRequest.Builder mutation(String url,String cookie,String csrf,String version,String key,String method,String body){var b=request(url,cookie).header("Idempotency-Key",key);if(csrf!=null)b.header("X-CSRF-TOKEN",csrf);if(version!=null)b.header("If-Match","\""+version+"\"");if(body!=null)b.header("Content-Type","application/json");return b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));}
 static HttpResponse<String> send(HttpClient http,HttpRequest.Builder b)throws Exception{return http.send(b.timeout(Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.ofString());}
}
