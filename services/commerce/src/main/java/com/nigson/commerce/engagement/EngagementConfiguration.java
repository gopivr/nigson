package com.nigson.commerce.engagement;
import com.nigson.commerce.security.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class EngagementConfiguration {
 @Bean EngagementRelay engagementRelay(Environment e,WorkloadCredentials credentials,ActorDelegation delegation,JdbcSessionTrust sessions,CommerceAuthority authority){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("ENGAGEMENT_SECURITY_REQUIRED");return new EngagementRelay(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_EXPERIENCE_BASE_URL"),credentials::token,delegation,sessions,authority);}
}
