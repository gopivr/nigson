package com.nigson.commerce.authorization;

import com.nigson.commerce.platform.*;
import com.nigson.commerce.security.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class StaffAuthorizationTest {
    static final String ISSUER = "https://identity.synthetic.invalid/realms/test";
    JdbcTemplate jdbc; TransactionTemplate tx; StaffService service; JdbcSessionTrust sessions;
    Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
    List<UUID> fixtures = new ArrayList<>(); UUID admin, staff;
    @BeforeEach void setup() {
        String url = System.getenv("NIGSON_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null, "make authorization-check supplies isolated PostgreSQL");
        if (!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson")) throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");
        var source = new DriverManagerDataSource(url + "?currentSchema=commerce", "nigson_commerce_app", "mock-commerce-runtime");
        jdbc = new JdbcTemplate(source); var manager = new DataSourceTransactionManager(source); tx = new TransactionTemplate(manager);
        var metrics = new SimpleMeterRegistry();
        sessions = new JdbcSessionTrust(jdbc, tx, new SessionCodec("v1", Map.of("v1", new byte[32])), clock, Set.of("/account"));
        var repo = new JdbcStaffRepository(jdbc, new AuditWriter(jdbc, clock, metrics), clock);
        var queries = new TransactionTemplate(manager); queries.setReadOnly(true); queries.setIsolationLevel(2);
        service = new StaffService(repo, new PlatformStore(jdbc, manager, clock, metrics), sessions, queries, clock);
        admin = party("ADMIN"); staff = party("SALES");
    }
    @AfterEach void clean() {
        if (jdbc == null) return;
        for (UUID id : fixtures) { sessions.revokeSubject(id); jdbc.update("DELETE FROM commerce.staff_role_assignment WHERE party_id=? OR granted_by=?", id, id); }
        for (UUID id : fixtures) jdbc.update("DELETE FROM commerce.party WHERE id=?", id);
    }
    UUID party(String role) {
        UUID id = UUID.randomUUID(); fixtures.add(id);
        jdbc.update("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES (?,'test','test',?,?,?,'Synthetic staff')", id, ISSUER, id.toString(), id + "@example.invalid");
        if (role != null) jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,'test','test',?,?,?)", UUID.randomUUID(), id, role, id);
        return id;
    }
    ActorAssertion.Actor actor(UUID id, Instant mfa) { return new ActorAssertion.Actor(id, ISSUER, id.toString(), null, Set.of("ADMIN"), Set.of(), mfa); }
    Staff.Change roles(Staff.Role... roles) { return new Staff.Change(List.of(roles), "Synthetic test operator request"); }
    Staff.View change(UUID target, long version, Staff.Change change, String key) { return service.change(actor(admin, clock.instant()), target, version, change, key, UUID.randomUUID()); }
    String login(UUID id) {
        var pending = sessions.begin("/account"); sessions.consume(pending.sessionId(), pending.flow().state());
        return sessions.authenticate(pending.sessionId(), new SessionRecord(1, id, ISSUER, id.toString(), clock.instant(), clock.instant(), "mock-access", "mock-refresh", clock.instant().plusSeconds(300), JdbcSessionTrust.opaque(), SessionRecord.TokenState.READY));
    }
    @Test void versionReplayRevocationAuditHistoryAndNoopAreAtomic() {
        String session = login(staff); String key = UUID.randomUUID().toString();
        var granted = change(staff, 0, roles(Staff.Role.FINANCE), key);
        assertThat(granted.roles()).containsExactly(Staff.Role.FINANCE); assertThat(granted.version()).isEqualTo(1);
        assertThatThrownBy(() -> sessions.read(session, false)).hasMessage("ACTOR_TRUST_REJECTED");
        assertThat(change(staff, 0, roles(Staff.Role.FINANCE), key)).isEqualTo(granted);
        assertThatThrownBy(() -> change(staff, 0, roles(Staff.Role.ADMIN), key)).hasMessage("IDEMPOTENCY_HASH_MISMATCH");
        assertThatThrownBy(() -> change(staff, 0, roles(Staff.Role.ADMIN), UUID.randomUUID().toString())).hasMessage("VERSION_MISMATCH");
        long auditCount = jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?", Long.class, admin.toString());
        assertThat(auditCount).isEqualTo(2);
        var noop = change(staff, 1, roles(Staff.Role.FINANCE), UUID.randomUUID().toString()); assertThat(noop.version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?", Long.class, admin.toString())).isEqualTo(auditCount);
        var removed = change(staff, 1, roles(), UUID.randomUUID().toString()); assertThat(removed.roles()).isEmpty(); assertThat(removed.version()).isEqualTo(2);
        var regranted = change(staff, 2, roles(Staff.Role.SALES), UUID.randomUUID().toString()); assertThat(regranted.version()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.staff_role_assignment WHERE party_id=? AND role='SALES'", Integer.class, staff)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.staff_role_assignment WHERE party_id=? AND role='SALES' AND revoked_at IS NULL", Integer.class, staff)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT string_agg(new_value::text,'') FROM commerce.audit_record WHERE created_by=?", String.class, admin.toString())).doesNotContain("Synthetic test operator");
    }
    @Test void lastActiveAdministratorSurvivesConcurrentSelfRemoval() throws Exception {
        UUID other = party("ADMIN"); UUID suspended = party("ADMIN"); jdbc.update("UPDATE commerce.party SET status='SUSPENDED' WHERE id=?", suspended);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var work = new ArrayList<Future<Boolean>>();
            for (UUID id : List.of(admin, other)) work.add(pool.submit(() -> { gate.await(); try { service.change(actor(id, clock.instant()), id, 0, roles(), UUID.randomUUID().toString(), UUID.randomUUID()); return true; } catch (Problem.Failure rejected) { assertThat(rejected.code).isEqualTo(ProblemCode.AUTHORIZATION_RULE_VIOLATION); return false; } }));
            gate.countDown(); int successes = 0; for (var result : work) if (result.get(5, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.staff_role_assignment r JOIN commerce.party p ON p.id=r.party_id WHERE r.role='ADMIN' AND r.revoked_at IS NULL AND p.status='ACTIVE'", Integer.class)).isEqualTo(1);
    }
    @Test void duplicateRaceCreatesOnlyOneVersionAndAuditEffect() throws Exception {
        String key = UUID.randomUUID().toString(); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<String>>();
            for (int i=0;i<2;i++) tasks.add(pool.submit(() -> { gate.await(); try { return Long.toString(change(staff, 0, roles(Staff.Role.FINANCE), key).version()); } catch (PlatformStore.Conflict failure) { return failure.getMessage(); } }));
            gate.countDown(); var outcomes = new ArrayList<String>(); for (var task : tasks) outcomes.add(task.get(5, TimeUnit.SECONDS));
            assertThat(outcomes).contains("1"); assertThat(outcomes).allMatch(v -> Set.of("1", "IDEMPOTENCY_IN_PROGRESS").contains(v));
        }
        assertThat(change(staff, 0, roles(Staff.Role.FINANCE), key).version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?", Integer.class, admin.toString())).isEqualTo(2);
    }
    @Test void rollbackRetainsRolesVersionSessionsAndRetry() {
        String session = login(staff), key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> { change(staff, 0, roles(Staff.Role.FINANCE), key); throw new IllegalStateException("Synthetic downstream failure"); })).hasMessage("Synthetic downstream failure");
        assertThat(sessions.read(session, false).party()).isEqualTo(staff);
        assertThat(jdbc.queryForObject("SELECT version FROM commerce.party WHERE id=?", Long.class, staff)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?", Integer.class, admin.toString())).isZero();
        assertThat(change(staff, 0, roles(Staff.Role.FINANCE), key).version()).isEqualTo(1);
    }
    @Test void lockContentionIsBoundedSafeAndRetryable() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var holding = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(711938201104::bigint)", Object.class);
                locked.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test lock deadline"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();
            String key = UUID.randomUUID().toString(); long began = System.nanoTime();
            try {
                assertThatThrownBy(() -> change(staff, 0, roles(Staff.Role.FINANCE), key)).satisfies(failure -> assertThat(new ProblemWriter("test", "").classify(failure)).isEqualTo(ProblemCode.CONFLICT));
                assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(3));
            } finally { release.countDown(); }
            holding.get(3, TimeUnit.SECONDS);
            assertThat(change(staff, 0, roles(Staff.Role.FINANCE), key).version()).isEqualTo(1);
        }
    }
    @Test void actualAuditInsertFailureRollsBackAndRetrySucceeds() {
        String session = login(staff), key = UUID.randomUUID().toString();
        var bootstrap = new JdbcTemplate(new DriverManagerDataSource(System.getenv("NIGSON_TEST_JDBC_URL"), "nigson_bootstrap", "mock-bootstrap"));
        bootstrap.execute("REVOKE INSERT ON commerce.audit_record FROM nigson_commerce_app");
        try {
            assertThatThrownBy(() -> change(staff, 0, roles(Staff.Role.FINANCE), key)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(sessions.read(session, false).party()).isEqualTo(staff);
            assertThat(jdbc.queryForObject("SELECT version FROM commerce.party WHERE id=?", Long.class, staff)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.staff_role_assignment WHERE party_id=? AND role='SALES' AND revoked_at IS NULL", Integer.class, staff)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.audit_record WHERE created_by=?", Integer.class, admin.toString())).isZero();
        } finally { bootstrap.execute("GRANT INSERT ON commerce.audit_record TO nigson_commerce_app"); }
        assertThat(change(staff, 0, roles(Staff.Role.FINANCE), key).version()).isEqualTo(1);
    }
    @Test void currentAuthorityMfaOwnershipAndSearchFailClosed() {
        UUID retail = party(null);
        assertThatThrownBy(() -> service.search(null, "", 0, 20, "createdAt,desc")).hasMessage("AUTH_REQUIRED");
        assertThatThrownBy(() -> service.search(actor(retail, clock.instant()), "", 0, 20, "createdAt,desc")).hasMessage("ACCESS_DENIED");
        assertThatThrownBy(() -> service.change(actor(admin, clock.instant().minusSeconds(901)), staff, 0, roles(), "mfa", UUID.randomUUID())).hasMessage("ACTOR_TRUST_REJECTED");
        assertThatThrownBy(() -> change(retail, 0, roles(Staff.Role.ADMIN), "retail")).hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> change(UUID.randomUUID(), 0, roles(), "missing")).hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> service.search(actor(admin, clock.instant()), "", 0, 101, "createdAt,desc")).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> service.search(actor(admin, clock.instant()), "", 0, 20, "identitySubject,asc")).hasMessage("VALIDATION_ERROR");
        var found = service.search(actor(admin, clock.instant()), staff.toString(), 0, 20, "email,asc"); assertThat(found.totalElements()).isEqualTo(1); assertThat(found.items().getFirst().id()).isEqualTo(staff);
        assertThat(service.search(actor(admin, clock.instant()), "%_' OR true --", 0, 20, "createdAt,desc").items()).isEmpty();
        jdbc.update("UPDATE commerce.staff_role_assignment SET revoked_at=now() WHERE party_id=?", admin);
        assertThatThrownBy(() -> change(staff, 0, roles(), "current")).hasMessage("ACCESS_DENIED");
    }
}
