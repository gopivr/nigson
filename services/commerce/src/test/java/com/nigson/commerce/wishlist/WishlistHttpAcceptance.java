package com.nigson.commerce.wishlist;
import java.util.*;import java.net.*;import java.net.http.*;import java.time.*;
import org.springframework.context.ConfigurableApplicationContext;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;
/** Real cookie/CSRF HTTP checks inside the isolated Boot test profile. */
public final class WishlistHttpAcceptance {
 private WishlistHttpAcceptance(){}
 public static void run(ConfigurableApplicationContext app,HttpClient http,String base)throws Exception{
  var f=new WishlistPersistenceTest();f.setup();var sessions=app.getBean(JdbcSessionTrust.class);var cookies=new HashMap<UUID,String>();var tokens=new HashMap<UUID,String>();
  try{
   for(UUID party:List.of(f.party,f.other)){var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());String csrf=JdbcSessionTrust.opaque();tokens.put(party,csrf);cookies.put(party,"__Host-nigson-session="+sessions.authenticate(flow.sessionId(),new SessionRecord(1,party,WishlistPersistenceTest.ISSUER,party.toString(),Instant.now(),null,"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),csrf,SessionRecord.TokenState.READY)));}
   String list=base+"/api/v1/me/wishlist",item=list+"/items/"+f.sku,cookie=cookies.get(f.party),csrf=tokens.get(f.party);
   assertThat(send(http,list,null,null,null,"GET").statusCode()).isEqualTo(401);
   assertThat(send(http,item,cookie,null,"no-csrf","PUT").statusCode()).isEqualTo(401);
   assertThat(send(http,item,cookie,"wrong-csrf","bad-csrf","PUT").statusCode()).isEqualTo(401);
   assertThat(send(http,item,cookie,csrf,null,"PUT").statusCode()).isEqualTo(400);
   assertThat(send(http,item,cookie,csrf,"save-http","PUT").statusCode()).isEqualTo(204);
   assertThat(send(http,item,cookie,csrf,"save-http","PUT").statusCode()).isEqualTo(204);
   var saved=send(http,list,cookie,null,null,"GET");assertThat(saved.statusCode()).isEqualTo(200);assertThat(saved.headers().firstValue("Cache-Control")).hasValue("no-store");assertThat(EventCodec.JSON.readTree(saved.body()).get(0).path("skuId").asString()).isEqualTo(f.sku.toString());assertThat(saved.body()).doesNotContain("email","identitySubject","synthetic-access");
   assertThat(EventCodec.JSON.readTree(send(http,list,cookies.get(f.other),null,null,"GET").body()).size()).isZero();
   assertThat(send(http,item,cookies.get(f.other),tokens.get(f.other),"other-delete","DELETE").statusCode()).isEqualTo(204);
   assertThat(EventCodec.JSON.readTree(send(http,list,cookie,null,null,"GET").body()).size()).isEqualTo(1);
   assertThat(send(http,list+"?partyId="+f.other,cookie,null,null,"GET").statusCode()).isEqualTo(400);
   f.jdbc.update("UPDATE commerce.sku SET active=false WHERE id=?",f.sku);assertThat(EventCodec.JSON.readTree(send(http,list,cookie,null,null,"GET").body()).get(0).path("available").asBoolean()).isFalse();
   assertThat(send(http,item,cookie,csrf,"remove-http","DELETE").statusCode()).isEqualTo(204);
   assertThat(EventCodec.JSON.readTree(send(http,list,cookie,null,null,"GET").body()).size()).isZero();
  }finally{sessions.revokeSubject(f.party);sessions.revokeSubject(f.other);f.cleanup();}
 }
 private static HttpResponse<String> send(HttpClient http,String url,String cookie,String csrf,String key,String method)throws Exception{var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10));if(cookie!=null)request.header("Cookie",cookie);if(csrf!=null)request.header("X-CSRF-TOKEN",csrf);if(key!=null)request.header("Idempotency-Key",key);if(!method.equals("GET"))request.header("Origin","https://bff.synthetic.test");return http.send(request.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());}
}
