package com.nigson.experience.lead;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;

final class DatabaseLeadRateLimiter implements LeadService.RateLimiter {
    private static final String UPSERT = """
            INSERT INTO experience.lead_rate_limit
                (id, created_by, updated_by, source_hash, window_start, request_count)
            VALUES (?, 'public:lead-rate-limit', 'public:lead-rate-limit', ?, ?, 1)
            ON CONFLICT (source_hash) DO UPDATE SET
                updated_at = now(),
                updated_by = 'public:lead-rate-limit',
                version = experience.lead_rate_limit.version + 1,
                window_start = CASE
                    WHEN experience.lead_rate_limit.window_start < EXCLUDED.window_start THEN EXCLUDED.window_start
                    ELSE experience.lead_rate_limit.window_start
                END,
                request_count = CASE
                    WHEN experience.lead_rate_limit.window_start < EXCLUDED.window_start THEN 1
                    ELSE experience.lead_rate_limit.request_count + 1
                END
            RETURNING request_count
            """;

    private final JdbcTemplate jdbc;
    private final byte[] key;
    private final int limit;
    private final Clock clock;
    private final AtomicLong lastCleanupMinute = new AtomicLong(Long.MIN_VALUE);

    DatabaseLeadRateLimiter(JdbcTemplate jdbc, String encodedKey, int limit, Clock clock) {
        if (jdbc == null || clock == null || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("LEAD_RATE_LIMIT_CONFIGURATION_INVALID");
        }
        try {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length < 32) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("LEAD_RATE_LIMIT_HMAC_KEY_REQUIRED");
        }
        this.jdbc = jdbc;
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    public boolean allow(String source) {
        if (source == null || source.isBlank() || source.length() > 256) return false;
        Instant now = clock.instant();
        long minute = Math.floorDiv(now.getEpochSecond(), 60);
        Instant windowStart = Instant.ofEpochSecond(minute * 60);
        String sourceHash = hash(source);
        Integer count = jdbc.queryForObject(UPSERT, Integer.class,
                UUID.randomUUID(), sourceHash, Timestamp.from(windowStart));
        if (count == null) throw new IllegalStateException("LEAD_RATE_LIMIT_STORAGE_INVALID");
        cleanup(minute);
        return count <= limit;
    }

    private void cleanup(long minute) {
        long previous = lastCleanupMinute.get();
        if (previous >= minute || !lastCleanupMinute.compareAndSet(previous, minute)) return;
        jdbc.update("DELETE FROM experience.lead_rate_limit WHERE window_start < ?",
                Timestamp.from(Instant.ofEpochSecond((minute - 2) * 60)));
    }

    private String hash(String source) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("LEAD_RATE_LIMIT_HASH_UNAVAILABLE");
        }
    }

    @Override
    public String toString() {
        return "DatabaseLeadRateLimiter[protected]";
    }
}
