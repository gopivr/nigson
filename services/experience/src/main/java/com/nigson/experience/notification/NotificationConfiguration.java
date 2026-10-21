package com.nigson.experience.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.experience.platform.*;

@Configuration(proxyBeanMethods=false)
public class NotificationConfiguration {
    @Bean NotificationOutcomeService notificationOutcomeService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,MeterRegistry metrics){return new NotificationOutcomeService(jdbc,store,audit,metrics);}
}
