package com.nigson.commerce.session;

import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RegistrationServiceTest {
    private static final String SECRET = "synthetic-registration-secret-long-enough";

    @Test void createsOnlyValidatedUsersAndDoesNotLeakExistence() throws Exception {
        var creates = new AtomicInteger();
        try (var keycloak = server(creates, false); var http = HttpClient.newHttpClient()) {
            var service = service(keycloak, http);
            service.register(valid("customer@example.invalid"), "127.0.0.1");
            service.register(valid("customer@example.invalid"), "127.0.0.2");
            assertThat(creates).hasValue(2);
            assertThatThrownBy(() -> service.register(new RegistrationService.Input("<script>", "Smith", "bad", "short"), "127.0.0.3"))
                .isInstanceOf(Problem.Failure.class).extracting("code").isEqualTo(ProblemCode.VALIDATION_ERROR);
        }
    }

    @Test void enforcesLocalOnlyClientAndPerAddressRateLimit() throws Exception {
        assertThatThrownBy(() -> new RegistrationService("prod", "https://id.example/realms/nigson", "nigson-registration", SECRET))
            .isInstanceOf(IllegalArgumentException.class);
        var creates = new AtomicInteger();
        try (var keycloak = server(creates, false); var http = HttpClient.newHttpClient()) {
            var service = service(keycloak, http);
            for (int i = 0; i < 5; i++) service.register(valid("customer" + i + "@example.invalid"), "127.0.0.9");
            assertThatThrownBy(() -> service.register(valid("customer6@example.invalid"), "127.0.0.9"))
                .isInstanceOf(Problem.Failure.class).extracting("code").isEqualTo(ProblemCode.RATE_LIMITED);
            assertThat(creates).hasValue(5);
        }
    }

    @Test void providerFailureIsSafeAndDoesNotClaimRegistration() throws Exception {
        var creates = new AtomicInteger();
        try (var keycloak = server(creates, true); var http = HttpClient.newHttpClient()) {
            assertThatThrownBy(() -> service(keycloak, http).register(valid("customer@example.invalid"), "127.0.0.4"))
                .isInstanceOf(Problem.Failure.class).extracting("code").isEqualTo(ProblemCode.PROVIDER_UNAVAILABLE);
        }
    }

    private static RegistrationService service(Mock server, HttpClient http) {
        return new RegistrationService("test", "http://127.0.0.1:" + server.server().getAddress().getPort() + "/realms/signup-test",
            "nigson-registration", SECRET, http);
    }
    private static RegistrationService.Input valid(String email) {
        return new RegistrationService.Input("Test", "Customer", email, "long-local-password");
    }
    private record Mock(HttpServer server) implements AutoCloseable { public void close() { server.stop(0); } }
    private static Mock server(AtomicInteger creates, boolean failCreate) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/signup-test/protocol/openid-connect/token", x -> {
            byte[] body = x.getRequestBody().readNBytes(4096);
            String request = new String(body, StandardCharsets.UTF_8);
            int status = request.contains("client_id=nigson-registration") && request.contains("client_secret=" + SECRET) ? 200 : 401;
            byte[] result = (status == 200 ? "{\"access_token\":\"synthetic-admin-token\",\"token_type\":\"Bearer\"}" : "{}").getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "application/json"); x.sendResponseHeaders(status, result.length); x.getResponseBody().write(result); x.close();
        });
        server.createContext("/admin/realms/signup-test/users", x -> {
            creates.incrementAndGet(); byte[] bytes = x.getRequestBody().readNBytes(8193);
            var json = EventCodec.JSON.readTree(bytes);
            if (!"Bearer synthetic-admin-token".equals(x.getRequestHeaders().getFirst("Authorization"))
                || !json.path("enabled").asBoolean() || json.path("emailVerified").asBoolean()
                || !"long-local-password".equals(json.path("credentials").get(0).path("value").asString())) {
                x.sendResponseHeaders(400, -1); x.close(); return;
            }
            int status = failCreate ? 503 : creates.get() == 2 ? 409 : 201;
            x.sendResponseHeaders(status, -1); x.close();
        });
        server.start(); return new Mock(server);
    }
}
