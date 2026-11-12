package com.nigson.commerce.market;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.catalogue.CatalogueMarketChanges;
@Configuration(proxyBeanMethods=false)
public class MarketConfiguration {
 @Bean MarketEligibility marketEligibility(JdbcTemplate jdbc,@org.springframework.beans.factory.annotation.Value("${APP_ENV}")String environment){return new JdbcMarketEligibility(jdbc,Clock.systemUTC(),environment);}
 @Bean MarketService marketService(JdbcTemplate jdbc,CatalogueMarketChanges catalogue,PlatformStore commands,AuditWriter audit,org.springframework.transaction.PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics,@org.springframework.beans.factory.annotation.Value("${NIGSON_MARKET_READ_BUDGET_PER_SECOND:1000}")int budget,@org.springframework.beans.factory.annotation.Value("${APP_ENV:local}")String environment,@org.springframework.beans.factory.annotation.Value("${NIGSON_LOCAL_SYNTHETIC_SELLING:false}")boolean localSynthetic){var clock=Clock.systemUTC();if(localSynthetic&&!environment.equals("local"))throw new IllegalArgumentException("LOCAL_SYNTHETIC_SELLING_REQUIRES_LOCAL_ENV");return new MarketService(new JdbcMarketRepository(jdbc,clock,localSynthetic),new MarketAuthority(jdbc,clock),catalogue,commands,audit,clock,jdbc,manager,metrics,budget);}
}
