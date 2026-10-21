package com.nigson.integration.notification;

import com.nigson.integration.platform.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
public class NotificationTemplateConfiguration {
    @Bean NotificationTemplateService notificationTemplateService(PlatformStore store,AuditWriter audit,MeterRegistry metrics,JdbcTemplate jdbc){return new NotificationTemplateService(store,audit,metrics,jdbc,Clock.systemUTC());}
}
