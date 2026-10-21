package com.nigson.experience.platform;

import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
import tools.jackson.databind.JsonNode;

public final class JdbcExperienceConfigurationRepository implements ExperienceConfigurationRepository {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public JdbcExperienceConfigurationRepository(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    private void transaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED"); }
    public void serialize(String key) {
        transaction(); jdbc.execute("SET LOCAL lock_timeout='500ms'"); jdbc.execute("SET LOCAL statement_timeout='2s'");
        try { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, "experience-configuration:" + key); }
        catch (org.springframework.dao.DataAccessException failure) {
            if (failure.getMostSpecificCause() instanceof java.sql.SQLException sql && Set.of("55P03", "57014").contains(sql.getSQLState()))
                throw new PlatformStore.Conflict("CONFIGURATION_BUSY");
            throw failure;
        }
    }
    public Snapshot latest(String key) {
        var rows = jdbc.query("SELECT id,revision,version,active FROM experience.feature_configuration WHERE key=? ORDER BY revision DESC LIMIT 1",
                (r, n) -> new Snapshot(r.getObject(1, UUID.class), r.getLong(2), r.getLong(3), r.getBoolean(4)), key);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void append(UUID id, String key, long revision, long version, JsonNode value, boolean active, ActorAssertion.Actor actor) {
        transaction(); String subject = actor.party().toString();
        jdbc.update("INSERT INTO experience.feature_configuration(id,created_by,updated_by,key,revision,version,value,active,approved_by) VALUES (?,?,?,?,?,?,?::jsonb,?,NULL)",
                id, subject, subject, key, revision, version, EventCodec.JSON.writeValueAsString(value), active);
    }
}
