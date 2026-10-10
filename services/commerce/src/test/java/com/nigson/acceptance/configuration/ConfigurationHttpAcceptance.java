package com.nigson.acceptance.configuration;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

public final class ConfigurationHttpAcceptance {
    private ConfigurationHttpAcceptance(){}
    public static void run(ConfigurableApplicationContext app,HttpClient http,String base)throws Exception{
        var jdbc=app.getBean(JdbcTemplate.class);var sessions=app.getBean(JdbcSessionTrust.class);
        String issuer="http://127.0.0.1:9/realms/offline";var admin=UUID.randomUUID();var retail=UUID.randomUUID();var ids=List.of(admin,retail);
        var cookies=new HashMap<UUID,String>();var csrf=new HashMap<UUID,String>();
        String route=base+"/api/v1/commerce/staff/configuration/catalogue.metadata";
        String body="{\"value\":{\"schemaVersion\":1,\"units\":[],\"divisions\":[]},\"active\":false,\"reason\":\"Synthetic configuration request\"}";
        try{
            for(var id:ids){
                jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic configuration actor')",id,issuer,id.toString(),id+"@example.invalid");
                if(id.equals(admin))jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'ADMIN',?)",UUID.randomUUID(),id,id);
                var flow=sessions.begin("/account");sessions.consume(flow.sessionId(),flow.flow().state());String token=JdbcSessionTrust.opaque();csrf.put(id,token);
                String session=sessions.authenticate(flow.sessionId(),new SessionRecord(1,id,issuer,id.toString(),Instant.now(),Instant.now().minusSeconds(901),"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),token,SessionRecord.TokenState.READY));cookies.put(id,"__Host-nigson-session="+session);
            }
            assertThat(send(http,HttpRequest.newBuilder(URI.create(route)).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body))).statusCode()).isEqualTo(401);
            assertThat(send(http,mutation(route,cookies.get(retail),csrf.get(retail),"0","denied",body)).statusCode()).isEqualTo(403);
            assertThat(send(http,mutation(route,cookies.get(admin),"wrong","0","csrf",body)).statusCode()).isEqualTo(401);
            for(String bad:List.of(body.replace("false","0"),body.replace("\"reason\":", "\"secret\":true,\"reason\":"),body.replace("\"units\":[]","\"units\":[],\"credential\":\"synthetic-secret\""),body.replace("Synthetic configuration request","short")))
                assertThat(send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0",UUID.randomUUID().toString(),bad)).statusCode()).isEqualTo(400);
            assertThat(send(http,mutation(route+"?unknown=1",cookies.get(admin),csrf.get(admin),"0","query",body)).statusCode()).isEqualTo(400);
            assertThat(send(http,mutation(route.replace("catalogue.metadata","unregistered"),cookies.get(admin),csrf.get(admin),"0","unregistered",body)).statusCode()).isEqualTo(422);
            assertThat(send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0","mfa",body.replace("false","true"))).statusCode()).isEqualTo(403);
            String key=UUID.randomUUID().toString();var response=send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0",key,body));
            assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().firstValue("ETag")).hasValue("\"1\"");assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(EventCodec.JSON.readTree(response.body()).properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("key","revision","active","version");
            assertThat(send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0",key,body)).body()).isEqualTo(response.body());
            assertThat(send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0",UUID.randomUUID().toString(),body)).statusCode()).isEqualTo(412);
            assertThat(send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0",key,body.replace("configuration request","changed configuration request"))).statusCode()).isEqualTo(409);
            jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",admin);
            assertThat(send(http,mutation(route,cookies.get(admin),csrf.get(admin),"0",key,body)).statusCode()).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.feature_configuration WHERE key='catalogue.metadata'",Integer.class)).isEqualTo(1);
        }finally{
            jdbc.update("DELETE FROM commerce.feature_configuration WHERE key='catalogue.metadata' AND created_by=?",admin.toString());
            for(var id:ids){sessions.revokeSubject(id);jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}
        }
    }
    private static HttpRequest.Builder mutation(String url,String cookie,String csrf,String version,String key,String body){return HttpRequest.newBuilder(URI.create(url)).header("Cookie",cookie).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/json").header("If-Match","\""+version+"\"").header("Idempotency-Key",key).PUT(HttpRequest.BodyPublishers.ofString(body));}
    private static HttpResponse<String> send(HttpClient http,HttpRequest.Builder request)throws Exception{return http.send(request.timeout(Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.ofString());}
}
