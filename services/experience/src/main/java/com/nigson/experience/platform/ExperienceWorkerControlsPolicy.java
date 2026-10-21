package com.nigson.experience.platform;

import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.AuditValues;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import java.util.Map;

/** Operational switches only; this does not approve or activate business features/providers. */
public final class ExperienceWorkerControlsPolicy implements ExperienceConfigurationPolicy {
    public String key() { return "experience.worker.controls"; }
    public void validate(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 2 || !value.has("relayEnabled") || !value.has("consumerEnabled")
                || !value.get("relayEnabled").isBoolean() || !value.get("consumerEnabled").isBoolean()) throw ApiValues.invalid();
    }
    public AuditChange apply(JdbcTemplate jdbc, JsonNode value, boolean active, java.util.UUID configurationId,
                             long revision, long version) {
        validate(value);
        return new AuditChange(AuditValues.Action.FEATURE_ACTIVATION, AuditValues.Resource.FEATURE, configurationId,
                AuditValues.Reason.CONFIGURATION_CHANGE, null,
                Map.of("key", key(), "active", active, "revision", revision, "version", version,
                        "relayEnabled", value.get("relayEnabled").asBoolean(),
                        "consumerEnabled", value.get("consumerEnabled").asBoolean()));
    }
}
