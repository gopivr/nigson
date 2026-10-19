package com.nigson.commerce.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;

/** Commerce-owned, read-only access to redacted audit evidence. */
public final class JdbcAuditQueryRepository implements AuditQueryRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuditQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public AuditValues.Page search(com.nigson.commerce.audit.AuditValues.Query query) {
        var clauses = new ArrayList<String>();
        var args = new ArrayList<Object>();
        if (query.resourceId() != null) { clauses.add("resource_id=?"); args.add(query.resourceId()); }
        if (query.action() != null) { clauses.add("action=?"); args.add(query.action()); }
        if (query.from() != null) { clauses.add("occurred_at>=?"); args.add(java.sql.Timestamp.from(query.from())); }
        if (query.to() != null) { clauses.add("occurred_at<=?"); args.add(java.sql.Timestamp.from(query.to())); }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        long total = java.util.Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.audit_record" + where, Long.class, args.toArray()));
        String order = switch (query.sort()) {
            case "occurredAt,asc" -> "occurred_at ASC,id ASC";
            case "actor,asc" -> "actor_subject ASC,occurred_at DESC,id DESC";
            case "actor,desc" -> "actor_subject DESC,occurred_at DESC,id DESC";
            case "action,asc" -> "action ASC,occurred_at DESC,id DESC";
            case "action,desc" -> "action DESC,occurred_at DESC,id DESC";
            case "resourceType,asc" -> "resource_type ASC,occurred_at DESC,id DESC";
            case "resourceType,desc" -> "resource_type DESC,occurred_at DESC,id DESC";
            default -> "occurred_at DESC,id DESC";
        };
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(query.size());
        pageArgs.add((long) query.page() * query.size());
        List<AuditValues.Item> items = jdbc.query(
                "SELECT id,actor_subject,action,resource_type,resource_id,occurred_at,reason,old_value,new_value FROM commerce.audit_record" + where +
                        " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (r, n) -> new AuditValues.Item(r.getObject("id", UUID.class), r.getString("actor_subject"),
                        r.getString("action"), r.getString("resource_type"), r.getObject("resource_id", UUID.class),
                        r.getTimestamp("occurred_at").toInstant(), r.getString("reason"), safeJson(r, "old_value"), safeJson(r, "new_value")),
                pageArgs.toArray());
        return new com.nigson.commerce.audit.AuditValues.Page(items, query.page(), query.size(), total,
                total == 0 ? 0 : (total + query.size() - 1) / query.size());
    }

    private static JsonNode safeJson(ResultSet row, String column) throws SQLException {
        String json = row.getString(column);
        if (json == null) return null;
        JsonNode value = EventCodec.JSON.readTree(json);
        if (!value.isObject()) throw new IllegalStateException("AUDIT_VALUE_INVALID");
        var fields = new java.util.LinkedHashMap<String, Object>();
        value.properties().forEach(e -> fields.put(e.getKey(), EventCodec.JSON.convertValue(e.getValue(), Object.class)));
        return com.nigson.shared.problem.AuditValues.redact(fields);
    }
}
