package com.nigson.experience.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseLeadRateLimiterTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T12:34:56Z"), ZoneOffset.UTC);

    @Test
    void acceptsRequestsThroughTheConfiguredWindowLimitAndDeniesExcess() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(UUID.class), anyString(), any(Timestamp.class)))
                .thenReturn(1, 3);
        var limiter = new DatabaseLeadRateLimiter(jdbc, KEY, 2, CLOCK);

        assertThat(limiter.allow("192.0.2.10")).isTrue();
        assertThat(limiter.allow("192.0.2.10")).isFalse();
    }

    @Test
    void storesOnlyKeyedHashesAndCleansExpiredWindows() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(UUID.class), anyString(), any(Timestamp.class)))
                .thenReturn(1);
        var limiter = new DatabaseLeadRateLimiter(jdbc, KEY, 10, CLOCK);

        assertThat(limiter.allow("192.0.2.10")).isTrue();

        var hashes = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(anyString(), eq(Integer.class),
                any(UUID.class), hashes.capture(), any(Timestamp.class));
        assertThat(hashes.getValue()).matches("[a-f0-9]{64}").isNotEqualTo("192.0.2.10");
        verify(jdbc).update(anyString(), any(Timestamp.class));
    }

    @Test
    void rejectsInvalidConfigurationAndSource() {
        var jdbc = mock(JdbcTemplate.class);
        assertThatThrownBy(() -> new DatabaseLeadRateLimiter(jdbc, "invalid", 10, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LEAD_RATE_LIMIT_HMAC_KEY_REQUIRED");

        var limiter = new DatabaseLeadRateLimiter(jdbc, KEY, 10, CLOCK);
        assertThat(limiter.allow(" ")).isFalse();
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }
}
