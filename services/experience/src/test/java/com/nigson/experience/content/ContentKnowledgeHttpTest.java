package com.nigson.experience.content;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nigson.experience.ExperienceApplication;
import com.nigson.shared.events.EventCodec;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class ContentKnowledgeHttpTest {
    private static final String BASE_BODY="{\"schemaVersion\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"Public content\"}]}";

    @Test void actualHttpEnforcesExactWorkloadAndImmediatelyHidesWithdrawnContent() throws Exception {
        String db=System.getenv("NIGSON_TEST_JDBC_URL"); Assumptions.assumeTrue(db!=null,"API-125/126 HTTP acceptance uses isolated PostgreSQL");
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var rsa=generator.generateKeyPair();
        var jwks=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String issuer="http://127.0.0.1:"+jwks.getAddress().getPort()+"/realms/offline";
        jwks.createContext("/",exchange->{byte[] body=EventCodec.JSON.writeValueAsBytes(Map.of("keys",List.of(new RSAKey.Builder((RSAPublicKey)rsa.getPublic()).keyID("synthetic").algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build().toJSONObject())));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});jwks.start();
        var props=new HashMap<String,Object>();props.put("APP_ENV","test");props.put("NIGSON_SECURITY_ENABLED","true");props.put("NIGSON_PRODUCT_PHASE","5");props.put("NIGSON_EVENTS_ENABLED","false");props.put("NIGSON_JDBC_URL",db+"?currentSchema=experience");props.put("NIGSON_DB_USER","nigson_experience_app");props.put("NIGSON_DB_PASSWORD","mock-experience-runtime");props.put("NIGSON_OIDC_ISSUER",issuer);props.put("NIGSON_OIDC_JWKS",issuer+"/protocol/openid-connect/certs");props.put("NIGSON_PRIVATE_PEER_CIDRS","127.0.0.1/32");props.put("NIGSON_ACTOR_PUBLIC_KEYS","{}");props.put("NIGSON_DISTRIBUTOR_REQUIRED_DOCUMENT_TYPES","CERTIFICATE,IDENTITY");props.put("NIGSON_DISTRIBUTOR_RECIPIENT_GROUP","synthetic-review-group");props.put("NIGSON_DISTRIBUTOR_COMMERCE_MODE","mock");props.put("NIGSON_MESSAGING_INBOUND_ENABLED","false");props.put("server.port","0");props.put("logging.level.root","WARN");
        UUID item=UUID.randomUUID();
        try(var app=new SpringApplicationBuilder(ExperienceApplication.class).properties(props).run();var http=HttpClient.newHttpClient()) {
            JdbcTemplate jdbc=app.getBean(JdbcTemplate.class);seed(jdbc,item);
            String base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
            String validation="/internal/v1/content/revision-validation";String content="/internal/v1/content/knowledge/"+item;
            String good=workload(rsa,issuer,"nigson-ai","knowledge.validate");
            assertThat(send(http,base+validation,null,null,"{\"items\":[{\"contentItemId\":\""+item+"\",\"revision\":1}]}").statusCode()).isEqualTo(401);
            assertThat(send(http,base+validation,workload(rsa,issuer,"nigson-commerce","knowledge.validate"),null,"{\"items\":[]}").statusCode()).isEqualTo(401);
            assertThat(send(http,base+validation,workload(rsa,issuer,"nigson-ai","knowledge.read"),null,"{\"items\":[]}").statusCode()).isEqualTo(401);
            assertThat(send(http,base+validation,good,"invalid-actor","{\"items\":[]}").statusCode()).isEqualTo(401);
            assertThat(send(http,base+validation,good,null,"{\"items\":[],\"extra\":true}").statusCode()).isEqualTo(400);
            assertThat(send(http,base+validation,good,null,"{\"items\":[]}").statusCode()).isEqualTo(400);
            String tooMany="{\"items\":["+java.util.stream.IntStream.range(0,21).mapToObj(i->"{\"contentItemId\":\""+item+"\",\"revision\":1}").collect(java.util.stream.Collectors.joining(","))+"]}";
            assertThat(send(http,base+validation,good,null,tooMany).statusCode()).isEqualTo(400);
            var validated=send(http,base+validation,good,null,"{\"items\":[{\"contentItemId\":\""+item+"\",\"revision\":1}]}");
            assertThat(validated.statusCode()).withFailMessage(validated.body()).isEqualTo(200);assertThat(validated.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(EventCodec.JSON.readTree(validated.body()).path("items").get(0).path("current").asBoolean()).isTrue();
            var wrongRoute=send(http,base+content,good,null,null);assertThat(wrongRoute.statusCode()).isEqualTo(401);
            var knowledge=send(http,base+content,workload(rsa,issuer,"nigson-ai","knowledge.read"),null,null);
            assertThat(knowledge.statusCode()).withFailMessage(knowledge.body()).isEqualTo(200);assertThat(knowledge.headers().firstValue("Cache-Control")).hasValue("no-store");
            var dto=EventCodec.JSON.readTree(knowledge.body());assertThat(dto.propertyNames()).containsExactlyInAnyOrder("id","type","slug","locale","revision","title","body","metadata","publishedAt","approved","checksum","visibility");assertThat(dto.path("approved").asBoolean()).isTrue();assertThat(dto.path("visibility").asString()).isEqualTo("PUBLIC");assertThat(dto.toString()).contains("Public content").doesNotContain("lead","application","DRAFT");
            jdbc.update("UPDATE experience.content_item SET current_published_revision_id=NULL,version=version+1 WHERE id=?",item);jdbc.update("UPDATE experience.content_revision SET state='WITHDRAWN',version=version+1 WHERE content_item_id=?",item);
            assertThat(send(http,base+content,workload(rsa,issuer,"nigson-ai","knowledge.read"),null,null).statusCode()).isEqualTo(404);
            var withdrawn=send(http,base+validation,good,null,"{\"items\":[{\"contentItemId\":\""+item+"\",\"revision\":1}]}");assertThat(EventCodec.JSON.readTree(withdrawn.body()).path("items").get(0).path("current").asBoolean()).isFalse();
        } finally { jwks.stop(0); }
    }

    private static void seed(JdbcTemplate jdbc,UUID id) {
        jdbc.update("INSERT INTO experience.content_item(id,created_by,updated_by,content_type,slug,locale,status) VALUES (?,'http-content-test','http-content-test','BLOG',?,'en-NG','ACTIVE')",id,"http-content-"+id.toString().substring(0,8));
        jdbc.update("INSERT INTO experience.content_revision(id,created_by,updated_by,content_item_id,revision,title,body,metadata,state,published_at,approved_by,checksum_sha256) VALUES (?,'http-content-test','http-content-test',?,1,'Public title',?::jsonb,'{\"title\":\"Public title\",\"description\":\"Public description\",\"canonicalUrl\":null,\"robots\":\"INDEX\",\"alternates\":[],\"structuredData\":{}}'::jsonb,'PUBLISHED',?,?,?)",UUID.randomUUID(),id,BASE_BODY,Timestamp.from(Instant.now().minusSeconds(5)),UUID.randomUUID(),"b".repeat(64));
        jdbc.update("UPDATE experience.content_item SET current_published_revision_id=(SELECT id FROM experience.content_revision WHERE content_item_id=?) WHERE id=?",id,id);
    }
    private static String workload(KeyPair pair,String issuer,String client,String scope) throws Exception {
        var jwt=new SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.RS256).keyID("synthetic").build(),new JWTClaimsSet.Builder().issuer(issuer).subject("synthetic-ai-workload").audience("nigson-experience").claim("typ","Bearer").claim("client_id",client).claim("azp",client).claim("scope",scope).issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(120))).build());jwt.sign(new RSASSASigner((RSAPrivateKey)pair.getPrivate()));return jwt.serialize();
    }
    private static HttpResponse<String> send(HttpClient http,String uri,String bearer,String actor,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(uri)).timeout(java.time.Duration.ofSeconds(10)).header("X-Correlation-ID","10000000-0000-4000-8000-000000000125");if(bearer!=null)builder.header("Authorization","Bearer "+bearer);if(actor!=null)builder.header("X-Nigson-Actor",actor);
        HttpRequest request=body==null?builder.GET().build():builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();return http.send(request,HttpResponse.BodyHandlers.ofString());
    }
}
