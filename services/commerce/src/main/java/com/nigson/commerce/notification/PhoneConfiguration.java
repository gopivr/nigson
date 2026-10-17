package com.nigson.commerce.notification;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import com.nigson.commerce.security.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_PHONE_CONSENT_ENABLED",havingValue="true")
public class PhoneConfiguration {
 @Bean PhoneRelay phoneRelay(Environment e,WorkloadCredentials credentials,ActorDelegation delegation,JdbcSessionTrust sessions,CommerceAuthority authority){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("PHONE_SECURITY_REQUIRED");return new PhoneRelay(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_EXPERIENCE_BASE_URL"),credentials::token,delegation,sessions,authority);}
}
