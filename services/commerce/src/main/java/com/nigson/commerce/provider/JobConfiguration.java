package com.nigson.commerce.provider;
import java.time.Clock;
import java.util.Set;
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
public class JobConfiguration {
    @Bean JobCommands jobCommands(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit){return new JobCommands(jdbc,manager,Clock.systemUTC(),audit);}
    @Bean public IntegrationJobs integrationJobs(Environment env,WorkloadCredentials credentials){
        String enabled=env.getProperty("NIGSON_INTEGRATION_JOBS_ENABLED","false");if(!Set.of("true","false").contains(enabled))throw new IllegalArgumentException("INTEGRATION_CONFIGURATION_REQUIRED");
        if(enabled.equals("true"))return new HttpIntegrationJobs(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token);
        return new IntegrationJobs(){public boolean configured(){return false;}public JobValues.Page list(JobValues.Query q,java.util.UUID c){throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);}public Accepted retry(java.util.UUID id,long v,JobValues.Retry b,String key,String actor,java.util.UUID c){throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);}};
    }
    @Bean JobService jobService(JobCommands commands,IntegrationJobs upstream,ActorDelegation delegation,MeterRegistry metrics){return new JobService(commands,upstream,delegation,metrics);}
}
