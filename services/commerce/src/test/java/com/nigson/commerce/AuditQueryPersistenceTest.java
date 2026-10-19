package com.nigson.commerce;

import com.nigson.commerce.audit.*;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.commerce.platform.JdbcTrace;
import com.nigson.shared.security.ActorAssertion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Native PostgreSQL coverage for API-134's Commerce-owned read boundary. */
class AuditQueryPersistenceTest {
    private JdbcTemplate jdbc;
    private JdbcTemplate bootstrap;
    private TransactionTemplate tx;
    private AuditWriter writer;
    private AuditQueryService service;
    private SimpleMeterRegistry metrics;
    private UUID admin, other, resource;
    private ActorAssertion.Actor adminActor, otherActor;
    private final String issuer = "http://127.0.0.1:9/realms/offline";

    @BeforeEach void setup() {
        String url = System.getenv("NIGSON_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null, "native Commerce harness supplies isolated PostgreSQL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nigson")) throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        metrics = new SimpleMeterRegistry();
        var source = new JdbcTrace(new DriverManagerDataSource(url + "?currentSchema=commerce", "nigson_commerce_app", "mock-commerce-runtime"), () -> metrics);
        jdbc = new JdbcTemplate(source);
        bootstrap = new JdbcTemplate(new DriverManagerDataSource(url, "nigson_bootstrap", "mock-bootstrap"));
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        writer = new AuditWriter(jdbc, Clock.systemUTC(), metrics);
        service = new AuditQueryService(actor -> {
            if (actor == null || actor.organization() != null || !actor.roles().contains("ADMIN") || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role='ADMIN' AND r.revoked_at IS NULL)", Boolean.class, actor.party(), actor.identityIssuer(), actor.identitySubject())))
                throw new com.nigson.shared.problem.Problem.Failure(com.nigson.shared.problem.ProblemCode.ACCESS_DENIED);
        }, new JdbcAuditQueryRepository(jdbc), new DataSourceTransactionManager(source), metrics);
        admin = UUID.randomUUID(); other = UUID.randomUUID(); resource = UUID.randomUUID();
        createParty(admin); createParty(other);
        jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,'ADMIN',?)", UUID.randomUUID(), admin, admin);
        adminActor = actor(admin, Set.of("ADMIN")); otherActor = actor(other, Set.of());
        append(resource, "private@example.invalid", "synthetic-token-secret");
        append(resource, "second@example.invalid", "second-secret");
    }

    private void createParty(UUID id) {
        jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic audit fixture')", id, issuer, id.toString(), id + "@example.invalid");
    }
    private ActorAssertion.Actor actor(UUID id, Set<String> roles) { return new ActorAssertion.Actor(id, issuer, id.toString(), null, roles, Set.of(), Instant.now()); }
    private UUID append(UUID target, String email, String token) {
        return tx.execute(s -> writer.append("synthetic-audit-actor", com.nigson.shared.problem.AuditValues.ActorKind.USER, com.nigson.shared.problem.AuditValues.Action.FEATURE_ACTIVATION,
                com.nigson.shared.problem.AuditValues.Resource.FEATURE, target, UUID.randomUUID(), com.nigson.shared.problem.AuditValues.Reason.CONFIGURATION_CHANGE,
                Map.of("enabled", false, "email", email), Map.of("enabled", true, "token", token), null));
    }

    @AfterEach void cleanup() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id IN (?,?) OR granted_by IN (?,?)", admin, other, admin, other);
        jdbc.update("DELETE FROM commerce.party WHERE id IN (?,?)", admin, other);
    }

    @Test void requiresCurrentAdminUsesBoundFiltersRedactsAndDoesNotWriteOnRead() {
        long before = jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record", Long.class);
        assertThatThrownBy(() -> service.search(otherActor, query(null, null, 0, 20, "occurredAt,desc"))).hasMessage("ACCESS_DENIED");
        var page = service.search(adminActor, query(resource, "FEATURE_ACTIVATION", 0, 1, "occurredAt,desc"));
        assertThat(page.totalElements()).isEqualTo(2); assertThat(page.totalPages()).isEqualTo(2); assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.resourceId()).isEqualTo(resource); assertThat(item.actor()).isEqualTo("synthetic-audit-actor");
        assertThat(item.oldValue().toString()).contains("redacted").doesNotContain("private@example.invalid");
        assertThat(item.newValue().toString()).contains("redacted").doesNotContain("synthetic-token-secret");
        var all = service.search(adminActor, query(resource, null, 0, 100, "occurredAt,desc"));
        assertThat(all.items()).hasSize(2);
        var next = service.search(adminActor, query(resource, null, 1, 1, "occurredAt,desc"));
        assertThat(next.totalElements()).isEqualTo(2); assertThat(next.items()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record", Long.class)).isEqualTo(before);
        assertThat(metrics.get("nigson.audit.queries").tag("owner", "commerce").counter().count()).isEqualTo(3);
    }

    @Test void currentRoleRevocationAndInvalidQueryAreDenied() {
        jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=? AND role='ADMIN'", admin);
        assertThatThrownBy(() -> service.search(adminActor, query(null, null, 0, 20, "occurredAt,desc"))).hasMessage("ACCESS_DENIED");
        assertThatThrownBy(() -> new AuditValues.Query(null, "DROP TABLE", null, null, 0, 20, "occurredAt,desc")).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> new AuditValues.Query(null, null, Instant.now(), Instant.now().minusSeconds(1), 0, 20, "occurredAt,desc")).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> new AuditValues.Query(null, null, null, null, 0, 20, "id,desc")).hasMessage("VALIDATION_ERROR");
    }

    @Test void runtimeRoleCannotMutateAuditAndReadFailureIsSafeAndReadOnly() {
        assertThatThrownBy(() -> jdbc.update("UPDATE commerce.audit_record SET reason='tampered' WHERE resource_id=?", resource)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM commerce.audit_record WHERE resource_id=?", resource)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        long before = jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=?", Long.class, resource);
        bootstrap.execute("REVOKE SELECT ON commerce.audit_record FROM nigson_commerce_app");
        try {
            assertThatThrownBy(() -> service.search(adminActor, query(null, null, 0, 20, "occurredAt,desc"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally { bootstrap.execute("GRANT SELECT ON commerce.audit_record TO nigson_commerce_app"); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE resource_id=?", Long.class, resource)).isEqualTo(before);
    }

    @Test void concurrentConsistentReadsAndAppendsHaveStablePerRequestPages() throws Exception {
        int readers = 8; var gate = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(readers + 1);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < readers; i++) jobs.add(pool.submit(() -> { gate.await(); var p = service.search(adminActor, query(resource, null, 0, 100, "occurredAt,desc")); assertThat(p.totalElements()).isEqualTo(p.items().size()); return null; }));
            jobs.add(pool.submit(() -> { gate.await(); for (int i = 0; i < 8; i++) append(resource, "race@example.invalid", "race-secret"); return null; }));
            gate.countDown(); for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        var finalPage = service.search(adminActor, query(resource, null, 0, 100, "occurredAt,desc"));
        assertThat(finalPage.totalElements()).isEqualTo(10); assertThat(finalPage.items()).hasSize(10);
        assertThat(finalPage.items().stream().map(AuditValues.Item::id).distinct()).hasSize(10);
    }

    private static AuditValues.Query query(UUID resource, String action, int page, int size, String sort) {
        return new AuditValues.Query(resource, action, null, null, page, size, sort);
    }
}
