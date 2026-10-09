package com.nigson.commerce.party;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class PartyConfiguration {
    @Bean PartyRepository partyRepository(JdbcTemplate jdbc,AuditWriter audit,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new JdbcPartyRepository(jdbc,Clock.systemUTC(),phase,audit);}
    @Bean PartyService partyService(PartyRepository repository,PlatformStore commands,PlatformTransactionManager manager,AuditWriter audit){var tx=new TransactionTemplate(manager);tx.setTimeout(2);return new PartyService(repository,commands,tx,audit,Clock.systemUTC());}
}
