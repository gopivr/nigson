package com.nigson.commerce.quote;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.*;
import com.nigson.commerce.security.WorkloadCredentials;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_QUOTES_ENABLED",havingValue="true")
public class QuoteConfiguration {
 @Bean QuoteLeadPort quoteLeadPort(Environment env,WorkloadCredentials credentials,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){if(phase<3)throw new IllegalArgumentException("QUOTE_PHASE_REQUIRED");return new HttpQuoteLeadPort(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_EXPERIENCE_BASE_URL"),credentials::token);}
 @Bean QuoteRepository quoteRepository(JdbcTemplate jdbc,PricingAuthority authority,Pricing pricing){return new JdbcQuoteRepository(jdbc,authority,pricing,Clock.systemUTC());}
 @Bean QuoteSkuEligibilityService quoteSkuEligibilityService(JdbcTemplate jdbc,PricingAuthority authority){return new QuoteSkuEligibilityService(jdbc,authority);}
 @Bean QuoteService quoteService(QuoteRepository repository,PricingAuthority authority,QuoteLeadPort leads,PlatformStore commands,AuditWriter audit,JdbcTemplate jdbc,MeterRegistry metrics,CheckoutQuoteRepository checkoutQuotes,com.nigson.commerce.order.OrderService orders){return new QuoteService(repository,authority,leads,commands,audit,jdbc,Clock.systemUTC(),metrics,checkoutQuotes,orders);}
}
