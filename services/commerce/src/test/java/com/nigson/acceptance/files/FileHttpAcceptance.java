package com.nigson.acceptance.files;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.files.*;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

public final class FileHttpAcceptance {
    private FileHttpAcceptance(){}
    /** Actual local JWKS and RSA-signed workload tokens through the production decoder. */
    public static final class Workload implements AutoCloseable {
        private final HttpServer server;private final KeyPair keys;public final String issuer;
        public Workload()throws Exception{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);issuer="http://127.0.0.1:"+server.getAddress().getPort()+"/realms/offline";byte[] jwks=EventCodec.JSON.writeValueAsBytes(Map.of("keys",List.of(new RSAKey.Builder((RSAPublicKey)keys.getPublic()).keyID("files-http").algorithm(JWSAlgorithm.RS256).keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE).build().toJSONObject())));server.createContext("/realms/offline/protocol/openid-connect/certs",exchange->{exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,jwks.length);exchange.getResponseBody().write(jwks);exchange.close();});server.start();}
        public String token(String scope,String client,String audience)throws Exception{var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("files-http").build(),new JWTClaimsSet.Builder().issuer(issuer).subject("synthetic-integration-http").audience(audience).issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("typ","Bearer").claim("azp",client).claim("client_id",client).claim("scope",scope).build());jwt.sign(new RSASSASigner(keys.getPrivate()));return jwt.serialize();}
        public void close(){server.stop(0);}
    }
    public static void configure(Map<String,Object> properties)throws Exception{
        if(System.getenv("NIGSON_TEST_STORAGE_FIXTURE")==null)return;var fixture=EventCodec.JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("NIGSON_TEST_STORAGE_FIXTURE"))));properties.put("NIGSON_FILES_ENABLED","true");properties.put("NIGSON_S3_LOCAL_ENDPOINT",fixture.path("endpoint").asString());properties.put("NIGSON_S3_LOCAL_CA",fixture.path("ca").asString());properties.put("NIGSON_AWS_REGION",fixture.path("region").asString());properties.put("NIGSON_S3_QUARANTINE_BUCKET",fixture.path("buckets").get(0).asString());properties.put("NIGSON_S3_PUBLIC_BUCKET",fixture.path("buckets").get(1).asString());properties.put("NIGSON_S3_PRIVATE_BUCKET",fixture.path("buckets").get(2).asString());
    }
    public static void run(ConfigurableApplicationContext app,HttpClient http,String base,Workload workload)throws Exception{
        if(workload==null)return;var jdbc=app.getBean(JdbcTemplate.class);var sessions=app.getBean(JdbcSessionTrust.class);var repo=app.getBean(FileRepository.class);
        UUID editor=UUID.randomUUID(),other=UUID.randomUUID(),retail=UUID.randomUUID();var ids=List.of(editor,other,retail);var cookies=new HashMap<UUID,String>();var csrf=new HashMap<UUID,String>();String prefix=base+"/api/v1/commerce/files/";
        try(var storage=new StorageFixture()){
            try{
                for(UUID id:ids){jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic HTTP file actor')",id,workload.issuer,id.toString(),id+"@example.invalid");if(!id.equals(retail))jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'CONTENT_EDITOR',?)",UUID.randomUUID(),id,id);var login=sessions.begin("/account");sessions.consume(login.sessionId(),login.flow().state());String token=JdbcSessionTrust.opaque();csrf.put(id,token);cookies.put(id,"__Host-nigson-session="+sessions.authenticate(login.sessionId(),new SessionRecord(1,id,workload.issuer,id.toString(),Instant.now(),Instant.now(),"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(300),token,SessionRecord.TokenState.READY)));}
                byte[] bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJ1kAAAAASUVORK5CYII=");String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));String input=EventCodec.JSON.writeValueAsString(Map.of("purpose","IMAGE","filename","Synthetic HTTP.png","mimeType","image/png","size",bytes.length,"checksumSha256",checksum));
                assertThat(send(http,mutation(prefix+"uploads",null,null,null,"anonymous",input)).statusCode()).isEqualTo(401);
                assertThat(send(http,mutation(prefix+"uploads",cookies.get(retail),csrf.get(retail),null,"retail",input)).statusCode()).isEqualTo(403);
                assertThat(send(http,mutation(prefix+"uploads",cookies.get(editor),"wrong",null,"csrf",input)).statusCode()).isEqualTo(401);
                assertThat(send(http,mutation(prefix+"uploads",cookies.get(editor),csrf.get(editor),null,"label",input.replace("IMAGE","SHIPPING_LABEL"))).statusCode()).isEqualTo(422);
                String key=UUID.randomUUID().toString();var created=send(http,mutation(prefix+"uploads",cookies.get(editor),csrf.get(editor),null,key,input));assertThat(created.statusCode()).isEqualTo(201);assertThat(created.headers().firstValue("ETag")).hasValue("\"0\"");assertThat(created.headers().firstValue("Location")).isPresent();assertThat(created.headers().firstValue("Cache-Control")).hasValue("no-store");
                var upload=EventCodec.JSON.readValue(created.body(),FileValues.UploadResult.class);assertThat(send(http,mutation(prefix+"uploads",cookies.get(editor),csrf.get(editor),null,key,input)).body()).isEqualTo(created.body());assertThat(storage.put(upload,bytes).statusCode()).isEqualTo(200);
                String completion=EventCodec.JSON.writeValueAsString(Map.of("checksumSha256",checksum));String finish=prefix+upload.assetId()+"/completion",download=prefix+upload.assetId()+"/download";
                assertThat(send(http,mutation(finish,cookies.get(other),csrf.get(other),"0","foreign",completion)).statusCode()).isEqualTo(403);
                assertThat(send(http,mutation(finish+"?unknown=1",cookies.get(editor),csrf.get(editor),"0","query",completion)).statusCode()).isEqualTo(400);
                var queued=send(http,mutation(finish,cookies.get(editor),csrf.get(editor),"0","completion",completion));assertThat(queued.statusCode()).isEqualTo(202);assertThat(queued.headers().firstValue("ETag")).hasValue("\"1\"");assertThat(queued.headers().firstValue("Location")).hasValue("/api/v1/commerce/files/"+upload.assetId()+"/download");assertThat(EventCodec.JSON.readTree(queued.body()).path("state").asString()).isEqualTo("QUARANTINED");
                assertThat(send(http,HttpRequest.newBuilder(URI.create(download)).header("Cookie",cookies.get(editor)).GET()).statusCode()).isEqualTo(409);
                assertThat(send(http,mutation(finish,cookies.get(editor),csrf.get(editor),"0","stale",completion)).statusCode()).isEqualTo(412);
                var stored=repo.find(upload.assetId(),false);String body=EventCodec.JSON.writeValueAsString(Map.of("commandId",stored.scanCommand(),"checksumSha256",checksum,"result","CLEAN","scannerVersion","synthetic-http-fixture-v1"));String callback=base+"/internal/v1/files/"+upload.assetId()+"/scan-result",scanKey=UUID.randomUUID().toString();
                assertThat(send(http,mutation(callback,cookies.get(editor),csrf.get(editor),"1",scanKey,body)).statusCode()).isEqualTo(401);
                for(var invalid:List.of(List.of("files.scan.read","nigson-integration","nigson-commerce"),List.of("files.scan.write","nigson-experience","nigson-commerce"),List.of("files.scan.write","nigson-integration","nigson-experience"))){String token=workload.token(invalid.get(0),invalid.get(1),invalid.get(2));assertThat(send(http,scan(callback,token,"1",scanKey,body)).statusCode()).isEqualTo(401);}
                String token=workload.token("files.scan.write","nigson-integration","nigson-commerce");var clean=send(http,scan(callback,token,"1",scanKey,body));assertThat(clean.statusCode()).isEqualTo(200);assertThat(clean.headers().firstValue("ETag")).hasValue("\"2\"");assertThat(EventCodec.JSON.readTree(clean.body()).path("url").isNull()).isTrue();assertThat(clean.body()).doesNotContain("objectKey","sourceVersion","uploadUrl","synthetic-refresh");assertThat(send(http,scan(callback,token,"1",scanKey,body)).body()).isEqualTo(clean.body());
                var allowed=send(http,HttpRequest.newBuilder(URI.create(download)).header("Cookie",cookies.get(editor)).GET());assertThat(allowed.statusCode()).isEqualTo(200);assertThat(allowed.headers().firstValue("Cache-Control")).hasValue("no-store");assertThat(storage.get(EventCodec.JSON.readTree(allowed.body()).path("url").asString()).body()).isEqualTo(bytes);
                assertThat(send(http,HttpRequest.newBuilder(URI.create(download)).header("Cookie",cookies.get(other)).GET()).statusCode()).isEqualTo(403);
                jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?",editor);assertThat(send(http,mutation(prefix+"uploads",cookies.get(editor),csrf.get(editor),null,key,input)).statusCode()).isEqualTo(403);
            }finally{for(UUID id:ids){jdbc.update("DELETE FROM commerce.file_transfer WHERE owner_party_id=?",id);jdbc.update("DELETE FROM commerce.asset_reference WHERE created_by=?",id.toString());jdbc.update("DELETE FROM commerce.spring_session WHERE principal_name=?",id.toString());jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=?",id);jdbc.update("DELETE FROM commerce.party WHERE id=?",id);}}
        }
    }
    private static HttpRequest.Builder mutation(String url,String cookie,String csrf,String version,String key,String body){var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").header("Idempotency-Key",key);if(cookie!=null)builder.header("Cookie",cookie);if(csrf!=null)builder.header("X-CSRF-TOKEN",csrf);if(version!=null)builder.header("If-Match","\""+version+"\"");return builder.POST(HttpRequest.BodyPublishers.ofString(body));}
    private static HttpRequest.Builder scan(String url,String token,String version,String key,String body){return mutation(url,null,null,version,key,body).header("Authorization","Bearer "+token);}
    private static HttpResponse<String> send(HttpClient http,HttpRequest.Builder builder)throws Exception{return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
}
