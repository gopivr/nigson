package com.nigson.experience.content;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.experience.platform.AuditWriter;

@Configuration(proxyBeanMethods=false)
@org.springframework.scheduling.annotation.EnableScheduling
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class ContentKnowledgeConfiguration {
    @Bean ContentKnowledgeRepository contentKnowledgeRepository(JdbcTemplate jdbc) { return new ContentKnowledgeRepository(jdbc); }
    @Bean ContentKnowledgeService contentKnowledgeService(JdbcTemplate jdbc, ContentKnowledgeRepository repository,
                                                          AuditWriter audit, PlatformTransactionManager manager,
                                                          MeterRegistry metrics) {
        return new ContentKnowledgeService(jdbc, repository, audit, manager, metrics);
    }
    @Bean CmsService cmsService(JdbcTemplate jdbc, PlatformTransactionManager manager, com.nigson.experience.platform.PlatformStore commands,
                                AuditWriter audit, MeterRegistry metrics, org.springframework.core.env.Environment env) {
        var raw=env.getProperty("NIGSON_CONTENT_ALLOWED_HOSTS","");var hosts=new java.util.HashSet<String>();if(!raw.isBlank())for(String host:raw.split(",",-1)){String normalized=host.strip().toLowerCase(java.util.Locale.ROOT);if(!normalized.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")||normalized.endsWith(".invalid"))throw new IllegalArgumentException("CONTENT_ALLOWED_HOSTS_INVALID");hosts.add(normalized);}
        var configuredMarkets=env.getProperty("NIGSON_CONTENT_MARKET_CODES","");var markets=new java.util.HashSet<String>();if(!configuredMarkets.isBlank())for(String market:configuredMarkets.split(",",-1)){String normalized=market.strip();if(!normalized.matches("[A-Z0-9_-]{1,32}"))throw new IllegalArgumentException("CONTENT_MARKET_CODES_INVALID");markets.add(normalized);}
        return new CmsService(jdbc, manager, commands, audit, metrics, java.time.Clock.systemUTC(), hosts, markets);
    }
    @Bean ContentPublicationScheduler contentPublicationScheduler(CmsService cms) { return new ContentPublicationScheduler(cms); }
}
