package com.nigson.integration.provider;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

class GoogleSheetsHttpAdapterTest {
 @Test void serviceAccountAssertionAndHttpRowsUseStableApplicationIdUpsertOnlyAgainstLocalStatefulMock() throws Exception {
  var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
  var tokenRequests=new AtomicInteger();var rows=Collections.synchronizedList(new ArrayList<List<String>>());
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/token",exchange->{tokenRequests.incrementAndGet();String form=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);String encoded=Arrays.stream(form.split("&")).filter(s->s.startsWith("assertion=")).findFirst().orElseThrow().substring("assertion=".length());String[] parts=URLDecoder.decode(encoded,StandardCharsets.UTF_8).split("\\.");assertThat(parts).hasSize(3);var claims=EventCodec.JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));assertThat(claims.path("iss").asString()).isEqualTo("sheets-test@example.invalid");assertThat(claims.path("scope").asString()).isEqualTo("https://www.googleapis.com/auth/spreadsheets");assertThat(claims.path("aud").asString()).isEqualTo("http://127.0.0.1:"+server.getAddress().getPort()+"/token");try{Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(keys.getPublic());verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();}catch(GeneralSecurityException invalid){throw new IllegalStateException(invalid);}send(exchange,200,"{\"access_token\":\"local-synthetic-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");});
  server.createContext("/v4/",exchange->{assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-synthetic-access-token");String path=exchange.getRequestURI().getPath();if("GET".equals(exchange.getRequestMethod())){List<List<String>> snapshot;synchronized(rows){snapshot=List.copyOf(rows);}send(exchange,200,snapshot.isEmpty()?"{}":EventCodec.JSON.writeValueAsString(Map.of("values",snapshot)));return;}var parsed=EventCodec.JSON.readTree(exchange.getRequestBody().readAllBytes());var cells=new ArrayList<String>();parsed.path("values").get(0).forEach(v->cells.add(v.asString()));if("POST".equals(exchange.getRequestMethod()))rows.add(cells);else{var matcher=java.util.regex.Pattern.compile("/values/Applicants!A([1-9][0-9]*):AZ\\1$").matcher(path);if(!matcher.find())throw new IllegalStateException("unexpected values update path");int index=Integer.parseInt(matcher.group(1))-1;while(rows.size()<=index)rows.add(new ArrayList<>());rows.set(index,cells);}send(exchange,200,"{\"updatedRows\":1}");});server.start();
  try {
   String privateKey=Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());var tokens=new GoogleServiceAccountTokenProvider("test","http://127.0.0.1:"+server.getAddress().getPort()+"/token","sheets-test@example.invalid",privateKey,Clock.systemUTC());var adapter=new GoogleSheetsHttpAdapter("test","http://127.0.0.1:"+server.getAddress().getPort(),"Applicants",tokens);
   UUID app=UUID.randomUUID();var answers=new TreeMap<String,String>(Map.of("portfolio","https://portfolio.example.invalid"));String submitted=Clock.systemUTC().instant().toString();var first=new CareerSheetRow(app,"Engineer","Applicant","applicant@example.invalid",submitted,"SUBMITTED",answers);String hash1="a".repeat(64),hash2="b".repeat(64);
   assertThat(adapter.safeToCreateAfterConfirmedAbsence()).isFalse();assertThat(adapter.contentHash("synthetic-sheet",app.toString())).isEmpty();adapter.upsert("synthetic-sheet",app.toString(),first,hash1,"stable-test-command");assertThat(adapter.contentHash("synthetic-sheet",app.toString())).contains(hash1);
   var changed=new CareerSheetRow(app,"Engineer","Applicant","applicant@example.invalid",submitted,"REVIEWED",answers);adapter.upsert("synthetic-sheet",app.toString(),changed,hash2,"stable-test-command");assertThat(adapter.contentHash("synthetic-sheet",app.toString())).contains(hash2);assertThat(rows).hasSize(1);assertThat(rows.getFirst().get(0)).isEqualTo(app.toString());assertThat(rows.getFirst().get(1)).isEqualTo(hash2);assertThat(tokenRequests.get()).isEqualTo(1);
  } finally {server.stop(0);}
 }
 private static void send(HttpExchange exchange,int status,String body)throws java.io.IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
}
