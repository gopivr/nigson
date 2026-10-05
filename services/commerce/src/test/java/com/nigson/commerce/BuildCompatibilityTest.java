package com.nigson.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.SpringBootVersion;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class BuildCompatibilityTest {
    @Test
    void requiredToolchainAndManagedLibrariesLink() {
        assertThat(Runtime.version().feature()).isEqualTo(25);
        assertThat(SpringBootVersion.getVersion()).isEqualTo("4.1.1");
        ApplicationModules.of(CommerceApplication.class).verify();
    }

    @Test
    void bootServerFactoryAndPatchedJsonMapperServeHttp() throws Exception {
        var mapper = JsonMapper.builder().build();
        var factory = new TomcatServletWebServerFactory(0);
        var server = factory.getWebServer(context -> {
            var registration = context.addServlet("dependency-compatibility", new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest request, HttpServletResponse response)
                        throws java.io.IOException {
                    response.setContentType("application/json");
                    response.getWriter().write(mapper.writeValueAsString(Map.of("status", "compatible")));
                }
            });
            registration.addMapping("/compatibility");
        });
        try {
            server.start();
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.getPort() + "/compatibility"))
                        .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(mapper.readTree(response.body()).get("status").asString()).isEqualTo("compatible");
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }
}
