package com.nigson.integration.notification;
import java.time.*;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.integration.platform.AuditWriter;import com.nigson.integration.providers.TwilioAdapter;import com.nigson.shared.problem.ApiValues;import com.nigson.shared.security.ProviderHttp;
@Configuration(proxyBeanMethods=false) @EnableScheduling
@ConditionalOnProperty(name="NIGSON_MESSAGING_STAFF_REPLY_WORKER_ENABLED",havingValue="true")
public class StaffReplyWorkerConfiguration {
 private static void enabled(Environment e){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false)||!e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_ENABLED",Boolean.class,false)||!e.getProperty("NIGSON_MESSAGING_VERIFICATION_INTAKE_ENABLED",Boolean.class,false))throw new IllegalArgumentException("STAFF_REPLY_WORKER_PREREQUISITES_REQUIRED");long delay=e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_WORKER_DELAY_MS",Long.class,30000L);String approval=e.getRequiredProperty("NIGSON_MESSAGING_STAFF_REPLY_APPROVAL");if(delay<1000||delay>300000||!approval.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("STAFF_REPLY_WORKER_POLICY_REQUIRED");}
 @Bean StaffReplyExecution staffReplyExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,TwilioAdapter provider,Environment e,MeterRegistry metrics){enabled(e);return new StaffReplyExecution(jdbc,manager,audit,Clock.systemUTC(),provider.mode(),provider.accountFingerprint(),ApiValues.uuid(e.getRequiredProperty("NIGSON_MESSAGING_CREATION_GENERATION")),Instant.parse(e.getRequiredProperty("NIGSON_MESSAGING_CREATION_NOT_BEFORE")),metrics);}
 @Bean StaffReplyMessage staffReplyMessage(Environment e,StaffReplyCrypto crypto){enabled(e);return new StaffReplyMessage(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_TWILIO_STATUS_PUBLIC_URL"),crypto);}
 @Bean StaffReplyWorker staffReplyWorker(StaffReplyExecution execution,StaffReplySource source,StaffReplyMessage messages,StaffReplyCrypto crypto,TwilioAdapter provider){return new StaffReplyWorker(execution,source,messages,crypto,provider,Clock.systemUTC());}
}
