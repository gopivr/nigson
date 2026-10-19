package com.nigson.experience.careers;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nigson.experience.ExperienceApplication;
import com.nigson.shared.events.EventCodec;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class CareerUploadHttpTest {
 @Test void publicUploadSessionUsesAntiAbuseIdempotencyAndPersistsOnlyHash() throws Exception {
  String jdbcUrl=System.getenv("NIGSON_TEST_JDBC_URL");
  Assumptions.assumeTrue(jdbcUrl!=null,"career HTTP acceptance uses the isolated PostgreSQL harness");
  var rsaGenerator=KeyPairGenerator.getInstance("RSA");rsaGenerator.initialize(2048);var rsa=rsaGenerator.generateKeyPair();
  var ecGenerator=KeyPairGenerator.getInstance("EC");ecGenerator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));var ec=ecGenerator.generateKeyPair();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  String issuer="http://127.0.0.1:"+server.getAddress().getPort()+"/realms/offline";
  server.createContext("/",exchange->{byte[] body=EventCodec.JSON.writeValueAsBytes(Map.of("keys",List.of(new RSAKey.Builder((RSAPublicKey)rsa.getPublic()).keyID("synthetic").algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build().toJSONObject())));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  byte[] hashKey=new byte[32],cipherKey=new byte[32],spamKey=new byte[32];Arrays.fill(hashKey,(byte)31);Arrays.fill(cipherKey,(byte)32);Arrays.fill(spamKey,(byte)33);
  var properties=new HashMap<String,Object>();properties.put("APP_ENV","test");properties.put("NIGSON_SECURITY_ENABLED","true");properties.put("NIGSON_PRODUCT_PHASE","2");properties.put("NIGSON_EVENTS_ENABLED","false");properties.put("NIGSON_JDBC_URL",jdbcUrl+"?currentSchema=experience");properties.put("NIGSON_DB_USER","nigson_experience_app");properties.put("NIGSON_DB_PASSWORD","mock-experience-runtime");properties.put("NIGSON_OIDC_ISSUER",issuer);properties.put("NIGSON_OIDC_JWKS",issuer+"/protocol/openid-connect/certs");properties.put("NIGSON_PRIVATE_PEER_CIDRS","127.0.0.1/32");properties.put("NIGSON_ACTOR_PUBLIC_KEYS",EventCodec.JSON.writeValueAsString(Map.of("active",Base64.getEncoder().encodeToString(ec.getPublic().getEncoded()))));
  properties.put("NIGSON_CAREERS_ENABLED","true");properties.put("NIGSON_CAREERS_CIPHER_REVISION","synthetic");properties.put("NIGSON_CAREERS_HASH_KEY_VERSION","1");properties.put("NIGSON_CAREERS_LOCAL_HASH_KEY",Base64.getEncoder().encodeToString(hashKey));properties.put("NIGSON_CAREERS_LOCAL_CIPHER_KEY",Base64.getEncoder().encodeToString(cipherKey));properties.put("NIGSON_CAREERS_ANTISPAM_MODE","mock");properties.put("NIGSON_CAREERS_LOCAL_ANTISPAM_HMAC_KEY",Base64.getEncoder().encodeToString(spamKey));properties.put("NIGSON_CAREERS_RETENTION_DAYS","30");properties.put("NIGSON_CAREERS_PRIVACY_POLICY_VERSION","SYNTHETIC_HTTP_POLICY");properties.put("NIGSON_CAREERS_SHEET_RECIPIENT_GROUP","synthetic-career-group");properties.put("server.port","0");properties.put("logging.level.root","WARN");
  byte[] phoneHash=new byte[32],phoneCipher=new byte[32],inboundCipher=new byte[32];Arrays.fill(phoneHash,(byte)41);Arrays.fill(phoneCipher,(byte)42);Arrays.fill(inboundCipher,(byte)43);properties.put("NIGSON_MESSAGING_INBOUND_ENABLED","true");properties.put("NIGSON_PHONE_CIPHER_REVISION","synthetic");properties.put("NIGSON_PHONE_HASH_KEY_VERSION","1");properties.put("NIGSON_PHONE_LOCAL_HASH_KEY",Base64.getEncoder().encodeToString(phoneHash));properties.put("NIGSON_PHONE_LOCAL_CIPHER_KEY",Base64.getEncoder().encodeToString(phoneCipher));properties.put("NIGSON_INBOUND_LOCAL_CIPHER_KEY",Base64.getEncoder().encodeToString(inboundCipher));properties.put("NIGSON_INBOUND_RETENTION_DAYS","30");properties.put("NIGSON_TWILIO_ACCOUNT_SID","AC0123456789abcdef0123456789abcdef");properties.put("NIGSON_TWILIO_AUTH_TOKEN","synthetic-twilio-auth-token-never-for-live");properties.put("NIGSON_TWILIO_WHATSAPP_FROM","whatsapp:+2348031234567");properties.put("NIGSON_TWILIO_WHATSAPP_PUBLIC_URL","https://callbacks.synthetic.invalid/api/v1/webhooks/notifications/twilio/whatsapp");
  try(var app=new SpringApplicationBuilder(ExperienceApplication.class).properties(properties).run();var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
   String base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort(),path="/api/v1/career-upload-sessions";var mock=new LocalCareerAntiSpamMock(Base64.getEncoder().encodeToString(spamKey));String key=UUID.randomUUID().toString(),body=EventCodec.JSON.writeValueAsString(Map.of("antiSpamToken",mock.sign("synthetic-nonce-123456",key)));
   var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);long uploadSessionsBefore=jdbc.queryForObject("SELECT count(*) FROM experience.upload_session",Long.class);
   var invalid=post(http,base+path,UUID.randomUUID().toString(),"{\"antiSpamToken\":\"forged\"}");assertThat(invalid.statusCode()).isEqualTo(422);assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.upload_session",Long.class)).isEqualTo(uploadSessionsBefore);
   var created=post(http,base+path,key,body);assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);assertThat(created.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");var dto=EventCodec.JSON.readTree(created.body());assertThat(dto.propertyNames()).containsExactlyInAnyOrder("uploadToken","expiresAt","maximumBytes");String token=dto.path("uploadToken").asString();assertThat(token).hasSize(43);assertThat(dto.path("maximumBytes").asLong()).isEqualTo(5L*1024*1024);
   var replay=post(http,base+path,key,body);assertThat(replay.statusCode()).isEqualTo(201);assertThat(replay.body()).isEqualTo(created.body());
   String tokenHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.upload_session WHERE token_hash=? AND purpose='CAREER_CV' AND owner_party_id IS NULL AND maximum_bytes=5242880 AND claimed_application_id IS NULL",Long.class,tokenHash)).isEqualTo(1);String stored=jdbc.queryForObject("SELECT response_body::text FROM experience.idempotency_record WHERE principal_key='public:career' AND operation='API-157' AND request_key=?",String.class,key);assertThat(stored).doesNotContain(token).contains("protectedToken");
   assertThat(post(http,base+path,UUID.randomUUID().toString(),"{\"antiSpamToken\":\"x\",\"purpose\":\"DISTRIBUTOR_PROOF\"}").statusCode()).isEqualTo(400);assertThat(http.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);assertThat(jdbc.queryForObject("SELECT count(*) FROM experience.upload_session",Long.class)).isEqualTo(uploadSessionsBefore+1);
  } finally {server.stop(0);}
 }
 private static HttpResponse<String> post(HttpClient http,String url,String key,String body)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
}
