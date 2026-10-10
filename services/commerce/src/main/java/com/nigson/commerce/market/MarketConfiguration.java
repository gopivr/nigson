package com.nigson.commerce.market;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.catalogue.CatalogueMarketChanges;
@Configuration(proxyBeanMethods=false)
public class MarketConfiguration {
 @Bean MarketEligibility marketEligibility(JdbcTemplate jdbc,@org.springframework.beans.factory.annotation.Value("${APP_ENV}")String environment){return new JdbcMarketEligibility(jdbc,Clock.systemUTC(),environment);}
 @Bean MarketService marketService(JdbcTemplate jdbc,CatalogueMarketChanges catalogue,PlatformStore commands,AuditWriter audit,org.springframework.transaction.PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics,@org.springframework.beans.factory.annotation.Value("${NIGSON_MARKET_READ_BUDGET_PER_SECOND:1000}")int budget){var clock=Clock.systemUTC();return new MarketService(new JdbcMarketRepository(jdbc,clock),new MarketAuthority(jdbc,clock),catalogue,commands,audit,clock,jdbc,manager,metrics,budget);}
}
