package com.nigson.experience.platform;

import org.springframework.jdbc.core.JdbcTemplate;

/** Dynamic worker switches read only from the latest Experience-owned typed configuration. */
public final class WorkerControls {
    private final JdbcTemplate jdbc;
    public WorkerControls(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean enabled(String name) {
        if (!java.util.Set.of("relayEnabled", "consumerEnabled").contains(name)) throw new IllegalArgumentException("WORKER_CONTROL_INVALID");
        try {
            var rows = jdbc.query("SELECT active,(value->>?)::boolean FROM experience.feature_configuration WHERE key='experience.worker.controls' ORDER BY revision DESC LIMIT 1",
                    (r, n) -> new Boolean[]{r.getBoolean(1), (Boolean) r.getObject(2)}, name);
            if (rows.isEmpty() || !rows.getFirst()[0]) return true;
            return Boolean.TRUE.equals(rows.getFirst()[1]);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            return false;
        }
    }
}
