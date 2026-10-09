package com.nigson.commerce.session;
import com.nigson.commerce.security.*;
import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class SessionConfiguration {
 @Bean SessionService sessionService(JdbcSessionTrust sessions,SessionLifecycle lifecycle,CommerceAuthority authority,JdbcTemplate jdbc,PlatformTransactionManager manager,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){var tx=new TransactionTemplate(manager);tx.setTimeout(2);return new SessionService(sessions,lifecycle,authority,jdbc,tx,java.time.Clock.systemUTC(),phase);}
}
