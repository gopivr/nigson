package com.nigson.acceptance.files;

import com.nigson.commerce.files.*;
import com.nigson.shared.events.EventCodec;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.*;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

final class StorageFixture implements AutoCloseable {
    final JsonNode config;final HttpClient http;final FileStorage storage;
    StorageFixture()throws Exception{
        String fixture=System.getenv("NIGSON_TEST_STORAGE_FIXTURE");if(fixture==null)throw new IllegalArgumentException("TLS_STORAGE_FIXTURE_REQUIRED");
        config=EventCodec.JSON.readTree(Files.readString(Path.of(fixture)));var uri=URI.create(config.path("endpoint").asString());if(!uri.getScheme().equals("https")||!uri.getHost().equals("localhost"))throw new IllegalArgumentException("LOCAL_TLS_STORAGE_REQUIRED");
        var ca=CertificateFactory.getInstance("X.509");var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);try(var in=Files.newInputStream(Path.of(config.path("ca").asString()))){store.setCertificateEntry("mock",ca.generateCertificate(in));}var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(store);var tls=SSLContext.getInstance("TLS");tls.init(null,managers.getTrustManagers(),null);http=HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(2)).build();
        var env=new MockEnvironment().withProperty("APP_ENV","test").withProperty("NIGSON_FILES_ENABLED","true").withProperty("NIGSON_AWS_REGION",config.path("region").asString()).withProperty("NIGSON_S3_LOCAL_ENDPOINT",uri.toString()).withProperty("NIGSON_S3_LOCAL_CA",config.path("ca").asString()).withProperty("NIGSON_S3_QUARANTINE_BUCKET",config.path("buckets").get(0).asString()).withProperty("NIGSON_S3_PUBLIC_BUCKET",config.path("buckets").get(1).asString()).withProperty("NIGSON_S3_PRIVATE_BUCKET",config.path("buckets").get(2).asString());
        storage=new FileConfiguration().fileStorage(env);
    }
    HttpResponse<byte[]> put(FileValues.UploadResult upload,byte[] bytes)throws Exception{var request=HttpRequest.newBuilder(URI.create(upload.uploadUrl())).timeout(Duration.ofSeconds(5));upload.requiredHeaders().forEach((k,v)->{if(!k.equalsIgnoreCase("Content-Length"))request.header(k,v);});return http.send(request.PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofByteArray());}
    HttpResponse<byte[]> get(String url)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());}
    JsonNode control(String fault)throws Exception{var request=HttpRequest.newBuilder(URI.create(config.path("endpoint").asString()+"/__test")).timeout(Duration.ofSeconds(5)).header("X-Test-Control",config.path("admin").asString());if(fault!=null)request.POST(HttpRequest.BodyPublishers.ofString(EventCodec.JSON.writeValueAsString(Map.of("fault",fault))));else request.GET();var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);return EventCodec.JSON.readTree(response.body());}
    @Override public void close(){storage.close();http.close();}
}
