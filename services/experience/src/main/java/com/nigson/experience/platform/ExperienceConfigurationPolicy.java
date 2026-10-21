package com.nigson.experience.platform;

import com.nigson.shared.problem.AuditValues;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Closed, owner-local configuration contracts. Values must never contain credentials. */
public interface ExperienceConfigurationPolicy {
    record AuditChange(AuditValues.Action action, AuditValues.Resource resource, java.util.UUID resourceId,
                       AuditValues.Reason reason, java.util.Map<String, ?> before, java.util.Map<String, ?> after) {}
    String key();
    void validate(JsonNode value);
    AuditChange apply(JdbcTemplate jdbc, JsonNode value, boolean active, java.util.UUID configurationId,
                      long revision, long version);
}
