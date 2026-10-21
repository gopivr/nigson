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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var rsa=generator.generateKeyPair();var ecGenerator=KeyPairGenerator.getInstance("EC");ecGenerator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));var ec=ecGenerator.generateKeyPair();
        var jwks=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String issuer="http://127.0.0.1:"+jwks.getAddress().getPort()+"/realms/offline";
        jwks.createContext("/",exchange->{byte[] body=EventCodec.JSON.writeValueAsBytes(Map.of("keys",List.of(new RSAKey.Builder((RSAPublicKey)rsa.getPublic()).keyID("synthetic").algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build().toJSONObject())));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});jwks.start();
        var props=new HashMap<String,Object>();props.put("APP_ENV","test");props.put("NIGSON_SECURITY_ENABLED","true");props.put("NIGSON_PRODUCT_PHASE","5");props.put("NIGSON_EVENTS_ENABLED","false");props.put("NIGSON_JDBC_URL",db+"?currentSchema=experience");props.put("NIGSON_DB_USER","nigson_experience_app");props.put("NIGSON_DB_PASSWORD","mock-experience-runtime");props.put("NIGSON_OIDC_ISSUER",issuer);props.put("NIGSON_OIDC_JWKS",issuer+"/protocol/openid-connect/certs");props.put("NIGSON_PRIVATE_PEER_CIDRS","127.0.0.1/32");props.put("NIGSON_ACTOR_PUBLIC_KEYS",EventCodec.JSON.writeValueAsString(Map.of("active",java.util.Base64.getEncoder().encodeToString(ec.getPublic().getEncoded()))));props.put("NIGSON_DISTRIBUTOR_REQUIRED_DOCUMENT_TYPES","CERTIFICATE,IDENTITY");props.put("NIGSON_DISTRIBUTOR_RECIPIENT_GROUP","synthetic-review-group");props.put("NIGSON_DISTRIBUTOR_COMMERCE_MODE","mock");props.put("NIGSON_MESSAGING_INBOUND_ENABLED","true");props.put("NIGSON_LEAD_RECIPIENT_GROUP","synthetic-sales-triage");props.put("NIGSON_PHONE_CIPHER_REVISION","synthetic");props.put("NIGSON_PHONE_HASH_KEY_VERSION","1");props.put("NIGSON_CONTENT_MARKET_CODES","NG");props.put("NIGSON_CONTENT_PUBLICATION_INTERVAL_MS","100");byte[] phoneHash=new byte[32],phoneCipher=new byte[32],inboundCipher=new byte[32];java.util.Arrays.fill(phoneHash,(byte)7);java.util.Arrays.fill(phoneCipher,(byte)8);java.util.Arrays.fill(inboundCipher,(byte)9);props.put("NIGSON_PHONE_LOCAL_HASH_KEY",java.util.Base64.getEncoder().encodeToString(phoneHash));props.put("NIGSON_PHONE_LOCAL_CIPHER_KEY",java.util.Base64.getEncoder().encodeToString(phoneCipher));props.put("NIGSON_INBOUND_LOCAL_CIPHER_KEY",java.util.Base64.getEncoder().encodeToString(inboundCipher));props.put("NIGSON_INBOUND_RETENTION_DAYS","30");props.put("NIGSON_MESSAGING_STAFF_REPLY_TEMPLATES","STAFF_LEAD_REPLY");props.put("NIGSON_TWILIO_ACCOUNT_SID","AC0123456789abcdef0123456789abcdef");props.put("NIGSON_TWILIO_AUTH_TOKEN","synthetic-auth-token-never-for-live");props.put("NIGSON_TWILIO_WHATSAPP_FROM","whatsapp:+2348031234567");props.put("NIGSON_TWILIO_WHATSAPP_PUBLIC_URL","https://callbacks.synthetic.invalid/api/v1/webhooks/notifications/twilio/whatsapp");props.put("server.port","0");props.put("logging.level.root","WARN");
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
            var validationReplay=send(http,base+validation,good,null,"{\"items\":[{\"contentItemId\":\""+item+"\",\"revision\":1}]}");assertThat(validationReplay.body()).isEqualTo(validated.body());
            var wrongRoute=send(http,base+content,good,null,null);assertThat(wrongRoute.statusCode()).isEqualTo(401);
            var knowledge=send(http,base+content,workload(rsa,issuer,"nigson-ai","knowledge.read"),null,null);
            assertThat(knowledge.statusCode()).withFailMessage(knowledge.body()).isEqualTo(200);assertThat(knowledge.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(send(http,base+content,workload(rsa,issuer,"nigson-ai","knowledge.read"),null,null).body()).isEqualTo(knowledge.body());
            var dto=EventCodec.JSON.readTree(knowledge.body());assertThat(dto.propertyNames()).containsExactlyInAnyOrder("id","type","slug","locale","revision","title","body","metadata","publishedAt","approved","checksum","visibility");assertThat(dto.path("approved").asBoolean()).isTrue();assertThat(dto.path("visibility").asString()).isEqualTo("PUBLIC");assertThat(dto.toString()).contains("Public content").doesNotContain("lead","application","DRAFT");
            jdbc.update("UPDATE experience.content_item SET current_published_revision_id=NULL,version=version+1 WHERE id=?",item);jdbc.update("UPDATE experience.content_revision SET state='WITHDRAWN',version=version+1 WHERE content_item_id=?",item);
            assertThat(send(http,base+content,workload(rsa,issuer,"nigson-ai","knowledge.read"),null,null).statusCode()).isEqualTo(404);
            var withdrawn=send(http,base+validation,good,null,"{\"items\":[{\"contentItemId\":\""+item+"\",\"revision\":1}]}");assertThat(EventCodec.JSON.readTree(withdrawn.body()).path("items").get(0).path("current").asBoolean()).isFalse();

            UUID channel=UUID.randomUUID();jdbc.update("INSERT INTO experience.channel_configuration(id,created_by,updated_by,kind,key,public_value,active) VALUES (?,'cms-test','cms-test','MAP',?,'{\"label\":\"Synthetic HQ\",\"latitude\":0,\"longitude\":0}'::jsonb,false)",channel,"hq-"+channel.toString().substring(0,8));
            String slug="cms-test-"+UUID.randomUUID().toString();String createBody="{\"type\":\"BLOG\",\"slug\":\""+slug+"\",\"locale\":\"en-NG\",\"title\":\"Synthetic CMS title\",\"body\":{\"schemaVersion\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"CMS public body\"}]},\"metadata\":{\"title\":\"CMS title\",\"description\":\"CMS description\",\"canonicalUrl\":null,\"robots\":\"INDEX\",\"alternates\":[],\"structuredData\":{}}}";
            String editorScope="content.write_WITH_SIGNED_ACTOR",keyPrefix="cms-"+UUID.randomUUID();assertThat(send(http,base+"/api/v1/staff/content",workload(rsa,issuer,"nigson-commerce",editorScope),null,createBody).statusCode()).isEqualTo(401);assertThat(cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_PUBLISHER",createBody,keyPrefix+"-wrong-role",null).statusCode()).isEqualTo(403);
            var created=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_EDITOR",createBody,keyPrefix+"-create",null);assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);var revisionDto=EventCodec.JSON.readTree(created.body());UUID contentId=UUID.fromString(revisionDto.path("contentItemId").asString()),revisionId=UUID.fromString(revisionDto.path("id").asString());
            var replay=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_EDITOR",createBody,keyPrefix+"-create",null);assertThat(replay.statusCode()).isEqualTo(201);assertThat(replay.body()).isEqualTo(created.body());
            assertThat(cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_EDITOR",createBody.replace("Synthetic CMS title","Changed replay title"),keyPrefix+"-create",null).statusCode()).isEqualTo(409);
            String raceSlug="cms-race-"+UUID.randomUUID();String raceBody=createBody.replace(slug,raceSlug);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);HttpResponse<String> raceA,raceB;try{var first=pool.submit(()->{start.await();return cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_EDITOR",raceBody,keyPrefix+"-race",null);});var second=pool.submit(()->{start.await();return cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_EDITOR",raceBody,keyPrefix+"-race",null);});start.countDown();raceA=first.get();raceB=second.get();}finally{pool.shutdownNow();}assertThat(List.of(raceA.statusCode(),raceB.statusCode())).allSatisfy(status->assertThat(status).isIn(201,409));if(raceA.statusCode()==201&&raceB.statusCode()==201)assertThat(raceA.body()).isEqualTo(raceB.body());var raceReplay=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content",editorScope,"CONTENT_EDITOR",raceBody,keyPrefix+"-race",null);assertThat(raceReplay.statusCode()).withFailMessage(raceReplay.body()).isEqualTo(201);assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.content_item WHERE slug=?",Long.class,raceSlug)).isEqualTo(1L);
            var preview=cmsSend(http,base,rsa,ec,issuer,"GET","/api/v1/staff/content-revisions/"+revisionId+"/preview","content.read_WITH_SIGNED_ACTOR","CONTENT_EDITOR",null,null,null);assertThat(preview.statusCode()).isEqualTo(200);assertThat(preview.headers().firstValue("Cache-Control")).hasValue("no-store");assertThat(EventCodec.JSON.readTree(preview.body()).path("state").asString()).isEqualTo("DRAFT");
            String editBody="{\"title\":\"Synthetic CMS revision two\",\"body\":{\"schemaVersion\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"CMS public body\"}]},\"metadata\":{\"title\":\"CMS title\",\"description\":\"CMS description\",\"canonicalUrl\":null,\"robots\":\"INDEX\",\"alternates\":[],\"structuredData\":{}}}";var stale=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content/"+contentId+"/revisions",editorScope,"CONTENT_EDITOR",editBody,keyPrefix+"-stale","\"1\"");assertThat(stale.statusCode()).isEqualTo(412);
            var revised=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content/"+contentId+"/revisions",editorScope,"CONTENT_EDITOR",editBody,keyPrefix+"-revision","\"0\"");assertThat(revised.statusCode()).withFailMessage(revised.body()).isEqualTo(201);revisionId=UUID.fromString(EventCodec.JSON.readTree(revised.body()).path("id").asString());assertThat(EventCodec.JSON.readTree(revised.body()).path("revision").asInt()).isEqualTo(2);
            var reviewed=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content-revisions/"+revisionId+"/review","content.review_WITH_SIGNED_ACTOR","CONTENT_EDITOR","{\"reason\":\"Synthetic review complete\"}",keyPrefix+"-review","\"0\"");assertThat(reviewed.statusCode()).withFailMessage(reviewed.body()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(reviewed.body()).path("state").asString()).isEqualTo("REVIEW");
            var approved=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content-revisions/"+revisionId+"/approval","content.approve_WITH_SIGNED_ACTOR","CONTENT_PUBLISHER","{\"approve\":true,\"reason\":\"Synthetic approval complete\"}",keyPrefix+"-approval","\"1\"");assertThat(approved.statusCode()).withFailMessage(approved.body()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(approved.body()).path("state").asString()).isEqualTo("APPROVED");
            String publishBody="{\"action\":\"PUBLISH\",\"scheduleAt\":\""+Instant.now().plusSeconds(2)+"\",\"reason\":\"Synthetic publish\"}";var published=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content-revisions/"+revisionId+"/publication","content.publish_WITH_SIGNED_ACTOR","CONTENT_PUBLISHER",publishBody,keyPrefix+"-publish","\"2\"");assertThat(published.statusCode()).withFailMessage(published.body()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(published.body()).path("state").asString()).isEqualTo("APPROVED");assertThat(send(http,base+"/api/v1/content/BLOG/"+slug+"?locale=en-NG",null,null,null).statusCode()).isEqualTo(404);Thread.sleep(3500);assertThat(send(http,base+"/api/v1/content/BLOG/"+slug+"?locale=en-NG",null,null,null).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.outbox_event WHERE aggregate_id=? AND event_type='content.published.v1'",Long.class,contentId)).isEqualTo(1L);
            var publicRead=send(http,base+"/api/v1/content/BLOG/"+slug+"?locale=en-NG",null,null,null);assertThat(publicRead.statusCode()).isEqualTo(200);assertThat(publicRead.body()).contains("CMS public body");
            var channelUpdate=cmsSend(http,base,rsa,ec,issuer,"PUT","/api/v1/staff/channel-configurations/"+channel,"channels.write_WITH_SIGNED_ADMIN","ADMIN","{\"kind\":\"MAP\",\"key\":\"hq-"+channel.toString().substring(0,8)+"\",\"publicValue\":{\"label\":\"Synthetic HQ\",\"latitude\":1,\"longitude\":2},\"active\":true}",keyPrefix+"-channel","\"0\"");assertThat(channelUpdate.statusCode()).withFailMessage(channelUpdate.body()).isEqualTo(200);
            var channels=send(http,base+"/api/v1/channels",null,null,null);assertThat(channels.statusCode()).isEqualTo(200);assertThat(EventCodec.JSON.readTree(channels.body()).path("maps").toString()).contains("Synthetic HQ");
            var withdrawnCms=cmsSend(http,base,rsa,ec,issuer,"POST","/api/v1/staff/content-revisions/"+revisionId+"/publication","content.publish_WITH_SIGNED_ACTOR","CONTENT_PUBLISHER","{\"action\":\"WITHDRAW\",\"reason\":\"Synthetic withdrawal\"}",keyPrefix+"-withdraw","\"4\"");assertThat(withdrawnCms.statusCode()).withFailMessage(withdrawnCms.body()).isEqualTo(200);assertThat(send(http,base+"/api/v1/content/BLOG/"+slug+"?locale=en-NG",null,null,null).statusCode()).isEqualTo(404);assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.outbox_event WHERE aggregate_id=? AND event_type='content.withdrawn.v1'",Long.class,contentId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.idempotency_record WHERE operation IN ('API-078','API-080','API-081','API-082','API-086') AND state='SUCCEEDED'",Long.class)).isGreaterThanOrEqualTo(5L);
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
    private static HttpResponse<String> cmsSend(HttpClient http,String base,KeyPair rsa,KeyPair ec,String issuer,String method,String path,String scope,String role,String body,String key,String match)throws Exception{
        var route=com.nigson.shared.security.PrivateWorkload.ROUTES.stream().filter(r->r.scope().equals(scope)&&r.method().equals(method)&&path.matches(r.path().replaceAll("\\{[^}]+\\}","[0-9a-fA-F-]{36}").replace("/","\\/"))).findFirst().orElseThrow();
        String principalSeed=key==null?scope+":"+path:key;UUID party=UUID.nameUUIDFromBytes(principalSeed.getBytes(java.nio.charset.StandardCharsets.UTF_8)),correlation=UUID.fromString("10000000-0000-4000-8000-000000000125");Instant now=Instant.now();var actor=new com.nigson.shared.security.ActorAssertion.Actor(party,issuer,"synthetic-cms-actor",null,java.util.Set.of(role),java.util.Set.of(scope),role.equals("ADMIN")?now:null);
        byte[] bytes=body==null?new byte[0]:body.getBytes(java.nio.charset.StandardCharsets.UTF_8);var binding=new com.nigson.shared.security.ActorAssertion.Binding("experience",scope,method,path,correlation,com.nigson.shared.security.ActorAssertion.Binding.bodyHash(bytes));var jwt=new SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256).keyID("active").type(new com.nimbusds.jose.JOSEObjectType("nigson-actor+jwt")).build(),com.nigson.shared.security.ActorAssertion.claims(actor,binding,now));jwt.sign(new com.nimbusds.jose.crypto.ECDSASigner((java.security.interfaces.ECPrivateKey)ec.getPrivate()));
        String token=workload(rsa,issuer,"nigson-commerce",scope);var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(10)).header("Authorization","Bearer "+token).header("X-Nigson-Actor",jwt.serialize()).header("X-Correlation-ID",correlation.toString()).header("Accept","application/json");if(key!=null)b.header("Idempotency-Key",key);if(match!=null)b.header("If-Match",match);if(body!=null)b.header("Content-Type","application/json");HttpRequest req=b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();return http.send(req,HttpResponse.BodyHandlers.ofString());
    }
}
