package com.nigson.experience.platform;

import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.AuditValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

/** Explicit staff replay of one terminal Experience outbox event. */
public final class QueueReplayPolicy implements ExperienceConfigurationPolicy {
    public String key() { return "experience.queue.replay"; }
    public void validate(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 1 || !value.has("eventId") || !value.get("eventId").isTextual()) throw ApiValues.invalid();
        ApiValues.uuid(value.get("eventId").asString());
    }
    public AuditChange apply(JdbcTemplate jdbc, JsonNode value, boolean active, UUID configurationId,
                             long revision, long version) {
        validate(value);
        if (!active) throw new Problem.Failure(ProblemCode.PLATFORM_RULE_VIOLATION);
        UUID eventId = ApiValues.uuid(value.get("eventId").asString());
        var rows = jdbc.query("SELECT id,attempt_count FROM experience.outbox_event WHERE event_id=? AND state='FAILED' FOR UPDATE",
                (r, n) -> Map.<String,Object>of("id", r.getObject(1, UUID.class), "attemptCount", r.getInt(2)), eventId);
        if (rows.isEmpty()) throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
        var row = rows.getFirst();
        int changed = jdbc.update("UPDATE experience.outbox_event SET state='PENDING',lease_until=NULL,next_attempt_at=now(),version=version+1,updated_at=now(),updated_by='experience-operator' WHERE event_id=? AND state='FAILED'", eventId);
        if (changed != 1) throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
        return new AuditChange(AuditValues.Action.QUEUE_REPLAY, AuditValues.Resource.QUEUE, (UUID) row.get("id"),
                AuditValues.Reason.OPERATOR_REQUEST, Map.of("state", "FAILED", "attemptCount", row.get("attemptCount")),
                Map.of("state", "PENDING", "eventId", eventId.toString(), "configurationRevision", revision,
                        "configurationVersion", version));
    }
}
