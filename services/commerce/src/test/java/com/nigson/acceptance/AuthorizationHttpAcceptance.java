package com.nigson.acceptance;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

/** Exercises the real assembled Boot controllers, JSON mapper, trust filters and owned DB. */
public final class AuthorizationHttpAcceptance {
    private AuthorizationHttpAcceptance() {}
    public static void run(ConfigurableApplicationContext app, HttpClient http, String base) throws Exception {
        var jdbc = app.getBean(JdbcTemplate.class); var sessions = app.getBean(JdbcSessionTrust.class);
        UUID admin = UUID.randomUUID(), target = UUID.randomUUID(), retail = UUID.randomUUID();
        List<UUID> ids = List.of(admin, target, retail); String issuer = "http://127.0.0.1:9/realms/offline";
        try {
            for (UUID id : ids) jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic HTTP staff')", id, issuer, id.toString(), id + "@example.invalid");
            for (UUID id : List.of(admin, target)) jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,?,?)", UUID.randomUUID(), id, id.equals(admin) ? "ADMIN" : "SALES", admin);
            Map<UUID, String> cookie = new HashMap<>(), csrf = new HashMap<>();
            for (UUID id : ids) {
                var pending = sessions.begin("/account"); sessions.consume(pending.sessionId(), pending.flow().state());
                String token = JdbcSessionTrust.opaque(); csrf.put(id, token);
                String session = sessions.authenticate(pending.sessionId(), new SessionRecord(1, id, issuer, id.toString(), Instant.now(), Instant.now(), "synthetic-access", "synthetic-refresh", Instant.now().plusSeconds(300), token, SessionRecord.TokenState.READY));
                cookie.put(id, "__Host-nigson-session=" + session);
            }
            String list = base + "/api/v1/staff/users", command = base + "/api/v1/staff/users/" + target + "/roles";
            assertThat(send(http, HttpRequest.newBuilder(URI.create(list)).GET()).statusCode()).isEqualTo(401);
            assertThat(send(http, HttpRequest.newBuilder(URI.create(list)).header("Cookie", cookie.get(retail)).GET()).statusCode()).isEqualTo(403);
            var response = send(http, HttpRequest.newBuilder(URI.create(list + "?q=" + target)).header("Cookie", cookie.get(admin)).GET());
            assertThat(response.statusCode()).isEqualTo(200); assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
            var page = EventCodec.JSON.readTree(response.body()); assertThat(page.path("totalElements").asLong()).isEqualTo(1);
            assertThat(response.headers().allValues("Set-Cookie")).as("normal authenticated request must not rotate cookie").isEmpty();
            assertThat(page.path("items").get(0).properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("id", "displayName", "email", "roles", "version");
            String adminSession = cookie.get(admin).split("=", 2)[1];
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.spring_session WHERE session_id=?", Integer.class, adminSession)).as("admin session row after GET").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.spring_session_attributes a JOIN commerce.spring_session s ON s.primary_id=a.session_primary_id WHERE s.session_id=? AND a.attribute_name='nigson.identity'", Integer.class, adminSession)).as("identity attribute after GET").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT expiry_time FROM commerce.spring_session WHERE session_id=?", Long.class, adminSession)).as("expiry after GET").isGreaterThan(System.currentTimeMillis());
            assertThat(sessions.read(adminSession, false).party()).isEqualTo(admin);
            for (String query : List.of("?unknown=1", "?size=101", "?sort=identitySubject,asc", "?q=a&q=b"))
                assertThat(send(http, HttpRequest.newBuilder(URI.create(list + query)).header("Cookie", cookie.get(admin)).GET()).statusCode()).as("query %s", query).isEqualTo(400);
            String body = "{\"roles\":[\"FINANCE\"],\"reason\":\"Synthetic operator request\"}";
            assertThat(send(http, HttpRequest.newBuilder(URI.create(command)).header("Cookie", cookie.get(admin)).header("Content-Type", "application/json").header("If-Match", "\"0\"").header("Idempotency-Key", "csrf-denial").PUT(HttpRequest.BodyPublishers.ofString(body))).statusCode()).isEqualTo(401);
            for (String bad : List.of("{\"roles\":[0],\"reason\":\"Synthetic request\"}", "{\"roles\":[],\"reason\":1234567890123}", "{\"roles\":[\"CUSTOMER\"],\"reason\":\"Synthetic operator request\"}", "{\"roles\":[\"ADMIN\",\"ADMIN\"],\"reason\":\"Synthetic operator request\"}", "{\"roles\":[],\"reason\":\"short\"}", "{\"roles\":[],\"reason\":\"Synthetic request\",\"private\":true}", "{\"roles\":[],\"roles\":[\"ADMIN\"],\"reason\":\"Synthetic request\"}"))
                assertThat(send(http, mutation(command, cookie.get(admin), csrf.get(admin), "0", UUID.randomUUID().toString(), bad)).statusCode()).as("invalid JSON %s", bad).isEqualTo(400);
            var missing = mutation(command, cookie.get(admin), csrf.get(admin), "0", "missing-version", body); missing.setHeader("If-Match", "W/\"0\"");
            assertThat(send(http, missing).statusCode()).isEqualTo(400);
            assertThat(send(http, HttpRequest.newBuilder(URI.create(base + "/api/v1/me")).header("Cookie", cookie.get(target)).GET()).statusCode()).isEqualTo(200);
            String key = UUID.randomUUID().toString();
            var changed = send(http, mutation(command, cookie.get(admin), csrf.get(admin), "0", key, body));
            assertThat(changed.statusCode()).isEqualTo(200); assertThat(changed.headers().firstValue("ETag")).hasValue("\"1\"");
            assertThat(send(http, mutation(command, cookie.get(admin), csrf.get(admin), "0", key, body)).body()).isEqualTo(changed.body());
            assertThat(send(http, mutation(command, cookie.get(admin), csrf.get(admin), "0", UUID.randomUUID().toString(), body)).statusCode()).isEqualTo(412);
            assertThat(send(http, mutation(command, cookie.get(admin), csrf.get(admin), "0", key, body.replace("FINANCE", "ADMIN"))).statusCode()).isEqualTo(409);
            assertThat(send(http, HttpRequest.newBuilder(URI.create(list)).header("Cookie", cookie.get(target)).GET()).statusCode()).isEqualTo(401);
            String nonstaff = base + "/api/v1/staff/users/" + retail + "/roles";
            assertThat(send(http, mutation(nonstaff, cookie.get(admin), csrf.get(admin), "0", "nonstaff", body)).statusCode()).isEqualTo(404);
            String ownCommand = base + "/api/v1/staff/users/" + admin + "/roles";
            assertThat(send(http, mutation(ownCommand, cookie.get(admin), csrf.get(admin), "0", "last-admin", "{\"roles\":[],\"reason\":\"Synthetic operator request\"}")).statusCode()).isEqualTo(422);
            var staleFlow = sessions.begin("/account"); sessions.consume(staleFlow.sessionId(), staleFlow.flow().state());
            String staleCsrf = JdbcSessionTrust.opaque();
            String staleSession = sessions.authenticate(staleFlow.sessionId(), new SessionRecord(1, admin, issuer, admin.toString(), Instant.now(), Instant.now().minusSeconds(901), "synthetic-access", "synthetic-refresh", Instant.now().plusSeconds(300), staleCsrf, SessionRecord.TokenState.READY));
            assertThat(send(http, mutation(command, "__Host-nigson-session=" + staleSession, staleCsrf, "1", "stale-mfa", body)).statusCode()).isEqualTo(403);
            assertThat(changed.body()).doesNotContain("identityIssuer", "identitySubject", "synthetic-access", "synthetic-refresh");
        } finally {
            for (UUID id : ids) sessions.revokeSubject(id);
            for (UUID id : ids) jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=? OR granted_by=?", id, id);
            for (UUID id : ids) jdbc.update("DELETE FROM commerce.party WHERE id=?", id);
        }
    }
    private static HttpRequest.Builder mutation(String url, String cookie, String csrf, String version, String key, String body) {
        return HttpRequest.newBuilder(URI.create(url)).header("Cookie", cookie).header("X-CSRF-TOKEN", csrf).header("Content-Type", "application/json").header("If-Match", "\"" + version + "\"").header("Idempotency-Key", key).PUT(HttpRequest.BodyPublishers.ofString(body));
    }
    private static HttpResponse<String> send(HttpClient http, HttpRequest.Builder request) throws Exception {
        return http.send(request.timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
