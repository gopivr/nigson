package com.nigson.commerce.authorization;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.security.JdbcSessionTrust;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED", havingValue="true")
public class AuthorizationConfiguration {
    @Bean StaffRepository staffRepository(JdbcTemplate jdbc, AuditWriter audit) {
        return new JdbcStaffRepository(jdbc, audit, Clock.systemUTC());
    }
    @Bean StaffService staffService(StaffRepository repository, PlatformStore commands, JdbcSessionTrust sessions, PlatformTransactionManager manager) {
        var queries = new TransactionTemplate(manager); queries.setReadOnly(true); queries.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ); queries.setTimeout(2);
        return new StaffService(repository, commands, sessions, queries, Clock.systemUTC());
    }
}
