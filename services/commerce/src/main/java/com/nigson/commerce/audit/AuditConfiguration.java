package com.nigson.commerce.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.AuditAuthority;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public class AuditConfiguration {
    @Bean AuditQueryRepository auditQueryRepository(JdbcTemplate jdbc) { return new JdbcAuditQueryRepository(jdbc); }
    @Bean AuditQueryService auditQueryService(AuditAuthority authority, AuditQueryRepository repository,
            PlatformTransactionManager manager, MeterRegistry metrics) {
        return new AuditQueryService(authority, repository, manager, metrics);
    }
}
