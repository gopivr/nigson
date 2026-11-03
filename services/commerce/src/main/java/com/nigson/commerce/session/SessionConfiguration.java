package com.nigson.commerce.session;
import com.nigson.commerce.security.*;
import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class SessionConfiguration {
 @Bean SessionService sessionService(JdbcSessionTrust sessions,SessionLifecycle lifecycle,CommerceAuthority authority,JdbcTemplate jdbc,PlatformTransactionManager manager,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){var tx=new TransactionTemplate(manager);tx.setTimeout(2);return new SessionService(sessions,lifecycle,authority,jdbc,tx,java.time.Clock.systemUTC(),phase);}
 @Bean @ConditionalOnProperty(name="NIGSON_REGISTRATION_ENABLED",havingValue="true") RegistrationService registrationService(@Value("${APP_ENV}")String env,@Value("${NIGSON_OIDC_ISSUER}")String issuer,@Value("${NIGSON_OIDC_JWKS}")String jwks,@Value("${NIGSON_REGISTRATION_CLIENT_ID}")String client,@Value("${NIGSON_REGISTRATION_CLIENT_SECRET}")String secret){return new RegistrationService(env,issuer,jwks,client,secret);}
}
