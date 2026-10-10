package com.nigson.commerce.notification;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
public class NotificationConfiguration {
    @Bean NotificationRepository notificationRepository(JdbcTemplate jdbc){return new JdbcNotificationRepository(jdbc);}
    @Bean NotificationService notificationService(NotificationRepository repository,PlatformStore store,AuditWriter audit,PlatformTransactionManager manager,MeterRegistry metrics){return new NotificationService(repository,store,audit,manager,metrics);}
}
