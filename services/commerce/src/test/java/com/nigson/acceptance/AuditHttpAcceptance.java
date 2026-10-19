package com.nigson.acceptance;

import com.nigson.commerce.platform.AuditWriter;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.AuditValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** Actual HTTP acceptance for API-134 through the assembled BFF session/security stack. */
public final class AuditHttpAcceptance {
    private AuditHttpAcceptance() {}

    public static void run(ConfigurableApplicationContext app, HttpClient http, String base) throws Exception {
        if (System.getenv("NIGSON_TEST_AUDIT") == null) return;
        var jdbc = app.getBean(JdbcTemplate.class); var sessions = app.getBean(JdbcSessionTrust.class);
        var tx = new TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var writer = app.getBean(AuditWriter.class);
        UUID admin = UUID.randomUUID(), sales = UUID.randomUUID(), customer = UUID.randomUUID(), resource = UUID.randomUUID();
        String issuer = "http://127.0.0.1:9/realms/offline";
        var people = List.of(admin, sales, customer); var cookies = new HashMap<UUID, String>();
        String route = base + "/api/v1/commerce/staff/audit-records";
        try {
            for (UUID id : people) jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic audit HTTP fixture')", id, issuer, id.toString(), id + "@example.invalid");
            jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'ADMIN',?)", UUID.randomUUID(), admin, admin);
            jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'SALES',?)", UUID.randomUUID(), sales, admin);
            for (UUID id : people) {
                var flow = sessions.begin("/account"); sessions.consume(flow.sessionId(), flow.flow().state());
                String csrf = JdbcSessionTrust.opaque();
                String value = sessions.authenticate(flow.sessionId(), new SessionRecord(1, id, issuer, id.toString(), Instant.now(), Instant.now(), "synthetic-access", "synthetic-refresh", Instant.now().plusSeconds(300), csrf, SessionRecord.TokenState.READY));
                cookies.put(id, "__Host-nigson-session=" + value);
            }
            long before = jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=?", Long.class, resource);
            tx.execute(status -> {
                writer.append("synthetic-http-actor", AuditValues.ActorKind.USER, AuditValues.Action.FEATURE_ACTIVATION,
                        AuditValues.Resource.FEATURE, resource, UUID.randomUUID(), AuditValues.Reason.CONFIGURATION_CHANGE,
                        Map.of("enabled", false, "email", "private@example.invalid"), Map.of("enabled", true, "token", "synthetic-token-secret"), null);
                writer.append("synthetic-http-actor", AuditValues.ActorKind.USER, AuditValues.Action.FEATURE_ACTIVATION,
                        AuditValues.Resource.FEATURE, resource, UUID.randomUUID(), AuditValues.Reason.CONFIGURATION_CHANGE,
                        Map.of("enabled", true, "email", "private@example.invalid"), Map.of("enabled", false), null);
                return null;
            });

            assertThat(send(http, get(route, null)).statusCode()).isEqualTo(401);
            assertThat(send(http, get(route, cookies.get(sales))).statusCode()).isEqualTo(403);
            var pageResponse = send(http, get(route + "?resourceId=" + resource + "&action=FEATURE_ACTIVATION&from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z&size=1", cookies.get(admin)));
            assertThat(pageResponse.statusCode()).as("admin audit query: %s", pageResponse.body()).isEqualTo(200);
            assertThat(pageResponse.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(pageResponse.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(pageResponse.body()).doesNotContain("private@example.invalid", "synthetic-token-secret", "synthetic-access", "synthetic-refresh");
            var body = EventCodec.JSON.readTree(pageResponse.body());
            assertThat(body.path("totalElements").asLong()).isEqualTo(2); assertThat(body.path("totalPages").asLong()).isEqualTo(2);
            assertThat(body.path("items")).hasSize(1);
            assertThat(body.path("items").get(0).properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("id", "actor", "action", "resourceType", "resourceId", "occurredAt", "reason", "oldValue", "newValue");
            assertThat(body.path("items").get(0).path("oldValue").toString()).contains("redacted");
            assertThat(send(http, get(route + "?resourceId=" + resource + "&action=FEATURE_ACTIVATION&from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z&size=1", cookies.get(admin))).body()).isEqualTo(pageResponse.body());
            var secondPage = send(http, get(route + "?resourceId=" + resource + "&page=1&size=1&sort=occurredAt,asc", cookies.get(admin)));
            assertThat(secondPage.statusCode()).isEqualTo(200); assertThat(EventCodec.JSON.readTree(secondPage.body()).path("items")).hasSize(1);
            for (String query : List.of("?unknown=1", "?size=101", "?page=-1", "?sort=id,desc", "?action=DROP%20TABLE", "?action=FEATURE_ACTIVATION&action=SESSION_LOGIN", "?resourceId=not-a-uuid", "?from=2026-10-18T12:00:00Z&to=2026-10-17T12:00:00Z")) {
                var bad = send(http, get(route + query, cookies.get(admin)));
                assertThat(bad.statusCode()).as("query rejected %s", query).isEqualTo(400);
                assertThat(bad.body()).doesNotContain("DROP TABLE", "not-a-uuid");
            }
            var bodyRequest = HttpRequest.newBuilder(URI.create(route)).header("Cookie", cookies.get(admin)).header("Content-Type", "application/json").method("GET", HttpRequest.BodyPublishers.ofString("{}"));
            assertThat(send(http, bodyRequest).statusCode()).isEqualTo(400);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=?", Long.class, resource)).isEqualTo(before + 2);
            var queries = app.getBean(io.micrometer.core.instrument.MeterRegistry.class).get("nigson.audit.queries").tag("owner", "commerce").counter();
            assertThat(queries.count()).isEqualTo(3);
            var bootstrap = new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"), "nigson_bootstrap", "mock-bootstrap"));
            bootstrap.execute("REVOKE SELECT ON commerce.audit_record FROM nigson_commerce_app");
            try {
                var failed = send(http, get(route, cookies.get(admin)));
                assertThat(failed.statusCode()).isEqualTo(500);
                assertThat(failed.body()).doesNotContain("audit_record", "permission denied", "SELECT", "private@example.invalid", "synthetic-token-secret");
            } finally { bootstrap.execute("GRANT SELECT ON commerce.audit_record TO nigson_commerce_app"); }
            jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=? AND role='ADMIN'", admin);
            assertThat(send(http, get(route, cookies.get(admin))).statusCode()).isEqualTo(403);
        } finally {
            for (String cookie : cookies.values()) sessions.logout(cookie.substring(cookie.indexOf('=') + 1));
            jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id IN (?,?,?) OR granted_by IN (?,?,?)", admin, sales, customer, admin, sales, customer);
            jdbc.update("DELETE FROM commerce.party WHERE id IN (?,?,?)", admin, sales, customer);
        }
    }

    private static HttpRequest.Builder get(String uri, String cookie) {
        var request = HttpRequest.newBuilder(URI.create(uri)); if (cookie != null) request.header("Cookie", cookie); return request.GET();
    }
    private static HttpResponse<String> send(HttpClient http, HttpRequest.Builder builder) throws Exception {
        return http.send(builder.timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
