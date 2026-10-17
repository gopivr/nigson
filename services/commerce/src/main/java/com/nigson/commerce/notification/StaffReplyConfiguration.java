package com.nigson.commerce.notification;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import com.nigson.commerce.security.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_MESSAGING_STAFF_REPLY_ENABLED",havingValue="true")
public class StaffReplyConfiguration {
 @Bean StaffReplyRelay staffReplyRelay(Environment e,WorkloadCredentials credentials,ActorDelegation delegation,JdbcSessionTrust sessions,CommerceAuthority authority){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("STAFF_REPLY_SECURITY_REQUIRED");return new StaffReplyRelay(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token,delegation,sessions,authority);}
}
