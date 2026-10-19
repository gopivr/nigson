package com.nigson.experience.reporting;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.experience.platform.AuditWriter;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class ReportConfiguration {
    @Bean ReportRepository reportRepository(JdbcTemplate jdbc){return new JdbcReportRepository(jdbc);}
    @Bean ReportService reportService(JdbcTemplate jdbc,ReportRepository repository,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager tx){return new ReportService(jdbc,repository,audit,metrics,tx);}
}
