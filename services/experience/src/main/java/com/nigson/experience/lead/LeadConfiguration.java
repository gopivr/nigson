package com.nigson.experience.lead;

import java.time.Clock;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.experience.platform.*;

@Configuration(proxyBeanMethods=false)
public class LeadConfiguration {
    @Bean @ConditionalOnProperty(name="NIGSON_LEADS_ANTISPAM_MODE",havingValue="mock")
    LeadService.AntiSpamVerifier leadAntiSpam(Environment env){if(!Set.of("local","test").contains(env.getRequiredProperty("APP_ENV")))throw new IllegalArgumentException("LEAD_ANTISPAM_MOCK_LOCAL_ONLY");return new LocalLeadMocks.AntiSpam(env.getRequiredProperty("NIGSON_LEADS_LOCAL_ANTISPAM_HMAC_KEY"));}
    @Bean @ConditionalOnProperty(name="NIGSON_LEADS_RATE_LIMIT_MODE",havingValue="local")
    LeadService.RateLimiter leadRateLimit(Environment env){if(!Set.of("local","test").contains(env.getRequiredProperty("APP_ENV")))throw new IllegalArgumentException("LEAD_RATE_LIMIT_LOCAL_ONLY");return new LocalLeadMocks.RateLimit(env.getRequiredProperty("NIGSON_LEADS_RATE_LIMIT_PER_MINUTE",Integer.class),Clock.systemUTC());}
    @Bean LeadService leadService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager manager,
            ObjectProvider<LeadService.AntiSpamVerifier> antiSpam,ObjectProvider<LeadService.RateLimiter> rateLimit,Environment env){
        String recipient=env.getProperty("NIGSON_LEAD_RECIPIENT_GROUP","");String admin=env.getProperty("NIGSON_LEAD_ADMIN_BASE_URL","");
        if(!recipient.isBlank()&&!recipient.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))throw new IllegalArgumentException("LEAD_RECIPIENT_GROUP_INVALID");
        return new LeadService(jdbc,store,audit,metrics,manager,Clock.systemUTC(),antiSpam.getIfAvailable(),rateLimit.getIfAvailable(),recipient.isBlank()?null:recipient,admin.isBlank()?null:admin);
    }
}
