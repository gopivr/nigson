package com.nigson.experience.platform;

import java.util.UUID;
import com.nigson.shared.security.ActorAssertion;
import tools.jackson.databind.JsonNode;

/** Access to Experience-owned configuration rows only. */
public interface ExperienceConfigurationRepository {
    record Snapshot(UUID id, long revision, long version, boolean active) {}
    void serialize(String key);
    Snapshot latest(String key);
    void append(UUID id, String key, long revision, long version, JsonNode value, boolean active, ActorAssertion.Actor actor);
}
