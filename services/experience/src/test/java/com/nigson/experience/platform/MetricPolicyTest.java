package com.nigson.experience.platform;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MetricPolicyTest {
    @Test void workerControlMetricsAllowOnlyBoundedWorkerNames() {
        var policy=new MetricPolicy();
        var registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var allowed=Counter.builder("nigson.worker.paused").tags("owner","experience","worker","relay").register(registry).getId();
        var hostile=Counter.builder("nigson.worker.paused").tags("owner","experience","worker","customer-123").register(registry).getId();
        assertThat(policy.accept(allowed)).isEqualTo(io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL);
        assertThat(policy.accept(hostile)).isEqualTo(io.micrometer.core.instrument.config.MeterFilterReply.DENY);
    }
}
