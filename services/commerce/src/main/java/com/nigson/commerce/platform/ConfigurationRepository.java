package com.nigson.commerce.platform;

import java.util.UUID;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.security.ActorAssertion;

/** Owned persistence port; every mutation runs inside the command ledger transaction. */
public interface ConfigurationRepository {
    record Snapshot(UUID id,long revision,long version,boolean active){}
    void requireAdministrator(ActorAssertion.Actor actor);
    void serialize(String key,UUID actor);
    Snapshot latest(String key);
    UUID append(String key,long revision,long version,JsonNode value,boolean active,ActorAssertion.Actor actor);
}
