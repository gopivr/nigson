package com.nigson.commerce.notification;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.commerce.security.*;
import com.nigson.shared.problem.*;
import io.micrometer.core.instrument.MeterRegistry;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class TemplateConfiguration {
    @Bean TemplateCommands templateCommands(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit){return new TemplateCommands(jdbc,manager,Clock.systemUTC(),audit);}
    @Bean public TemplatePort templatePort(Environment env,WorkloadCredentials credentials){String enabled=env.getProperty("NIGSON_NOTIFICATION_TEMPLATES_ENABLED","false");if(!Set.of("true","false").contains(enabled))throw new IllegalArgumentException("NOTIFICATION_CONFIGURATION_REQUIRED");if(enabled.equals("true"))return new HttpTemplatePort(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token);
        return new TemplatePort(){public boolean configured(){return false;}public Accepted put(TemplateValues.Template t,Long v,String key,String actor,UUID c){throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);}};
    }
    @Bean TemplateService templateService(TemplateCommands commands,TemplatePort upstream,ActorDelegation delegation,MeterRegistry metrics){return new TemplateService(commands,upstream,delegation,metrics);}
}
