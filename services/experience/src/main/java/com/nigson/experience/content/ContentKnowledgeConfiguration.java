package com.nigson.experience.content;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.experience.platform.AuditWriter;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class ContentKnowledgeConfiguration {
    @Bean ContentKnowledgeRepository contentKnowledgeRepository(JdbcTemplate jdbc) { return new ContentKnowledgeRepository(jdbc); }
    @Bean ContentKnowledgeService contentKnowledgeService(JdbcTemplate jdbc, ContentKnowledgeRepository repository,
                                                          AuditWriter audit, PlatformTransactionManager manager,
                                                          MeterRegistry metrics) {
        return new ContentKnowledgeService(jdbc, repository, audit, manager, metrics);
    }
}
