package com.nigson.commerce.authorization;

import java.util.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;

/** Reuses the Party aggregate revision and existing partial active-role uniqueness. */
public final class JdbcStaffRepository implements StaffRepository, com.nigson.commerce.platform.AuditAuthority {
    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final Clock clock;
    public JdbcStaffRepository(JdbcTemplate jdbc, AuditWriter audit, Clock clock) {
        this.jdbc = jdbc; this.audit = audit; this.clock = clock;
    }
    public void serializeChanges() {
        jdbc.execute("SET LOCAL lock_timeout='1s'");
        try { jdbc.queryForObject("SELECT pg_advisory_xact_lock(711938201104::bigint)", Object.class); }
        catch (org.springframework.dao.DataAccessException failure) {
            if (failure.getMostSpecificCause() instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState()))
                throw new Problem.Failure(ProblemCode.CONFLICT);
            throw failure;
        }
    }
    public void lockParties(UUID actor, UUID target) {
        jdbc.query("SELECT id FROM commerce.party WHERE id IN (?,?) ORDER BY id FOR UPDATE",
                (r, n) -> r.getObject(1, UUID.class), actor, target);
    }
    public void requireAdministrator(ActorAssertion.Actor actor) {
        if (actor == null || actor.organization() != null || !actor.roles().contains("ADMIN"))
            throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        Boolean allowed = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role='ADMIN' AND r.revoked_at IS NULL)", Boolean.class,
                actor.party(), actor.identityIssuer(), actor.identitySubject());
        if (!Boolean.TRUE.equals(allowed)) throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
    @Override public void requireCurrentAdministrator(ActorAssertion.Actor actor) { requireAdministrator(actor); }
    private List<Staff.Role> roles(UUID id) {
        return jdbc.query("SELECT role FROM commerce.staff_role_assignment WHERE party_id=? AND revoked_at IS NULL ORDER BY role",
                (r, n) -> Staff.Role.valueOf(r.getString(1)), id);
    }
    public Staff.View find(UUID id) {
        var rows = jdbc.query("SELECT p.id,p.display_name,p.email,p.version FROM commerce.party p WHERE p.id=? AND p.status='ACTIVE' AND EXISTS(SELECT 1 FROM commerce.staff_role_assignment r WHERE r.party_id=p.id)",
                (r, n) -> new Staff.View(r.getObject(1, UUID.class), r.getString(2), r.getString(3), roles(id), r.getLong(4)), id);
        if (rows.size() != 1) throw new Problem.Failure(ProblemCode.NOT_FOUND);
        return rows.getFirst();
    }
    public Staff.Page search(String q, int page, int size, String sort) {
        // Only allowlisted fragments enter SQL; user search text always remains a bound value.
        String order = switch (sort) {
            case "createdAt,desc" -> "p.created_at DESC,p.id";
            case "createdAt,asc" -> "p.created_at ASC,p.id";
            case "displayName,asc" -> "p.display_name ASC,p.id";
            case "displayName,desc" -> "p.display_name DESC,p.id";
            case "email,asc" -> "p.email ASC,p.id";
            case "email,desc" -> "p.email DESC,p.id";
            default -> throw ApiValues.invalid();
        };
        String where = " FROM commerce.party p WHERE EXISTS(SELECT 1 FROM commerce.staff_role_assignment r WHERE r.party_id=p.id) AND (?='' OR strpos(lower(p.display_name),lower(?))>0 OR strpos(lower(p.email),lower(?))>0)";
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)" + where, Long.class, q, q, q));
        var items = jdbc.query("SELECT p.id,p.display_name,p.email,p.version" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (r, n) -> new Staff.View(r.getObject(1, UUID.class), r.getString(2), r.getString(3), roles(r.getObject(1, UUID.class)), r.getLong(4)), q, q, q, size, (long) page * size);
        return new Staff.Page(items, page, size, total, (total + size - 1) / size);
    }
    public long activeAdministrators() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM commerce.staff_role_assignment r JOIN commerce.party p ON p.id=r.party_id WHERE r.role='ADMIN' AND r.revoked_at IS NULL AND p.status='ACTIVE'", Long.class));
    }
    public void replace(UUID id, Staff.Change change, Staff.View before, ActorAssertion.Actor actor, UUID correlation) {
        String subject = actor.party().toString();
        for (var role : before.roles()) if (!change.roles().contains(role)) {
            UUID assignment = jdbc.queryForObject("UPDATE commerce.staff_role_assignment SET revoked_at=?,updated_at=?,updated_by=?,version=version+1 WHERE party_id=? AND role=? AND revoked_at IS NULL RETURNING id", UUID.class,
                    java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant()), subject, id, role.name());
            audit.append(subject, AuditValues.ActorKind.USER, AuditValues.Action.STAFF_ROLE_REVOKE, AuditValues.Resource.STAFF_ROLE, assignment, correlation,
                    AuditValues.Reason.OPERATOR_REQUEST, Map.of("role", role.name(), "reason", change.reason()), null, null);
        }
        for (var role : change.roles()) if (!before.roles().contains(role)) {
            UUID assignment = com.nigson.shared.events.EventCodec.uuid7(clock.instant());
            jdbc.update("INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES (?,?,?,?,?,?)", assignment, subject, subject, id, role.name(), actor.party());
            audit.append(subject, AuditValues.ActorKind.USER, AuditValues.Action.STAFF_ROLE_GRANT, AuditValues.Resource.STAFF_ROLE, assignment, correlation,
                    AuditValues.Reason.OPERATOR_REQUEST, null, Map.of("role", role.name(), "reason", change.reason()), null);
        }
        if (jdbc.update("UPDATE commerce.party SET version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?", java.sql.Timestamp.from(clock.instant()), subject, id, before.version()) != 1) throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    }
}
