package com.nigson.commerce.pricing;
import java.time.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.market.MarketEligibility;
@Configuration(proxyBeanMethods=false)
public class PricingConfiguration {
 @Bean PricingAuthority pricingAuthority(JdbcTemplate jdbc,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,@Value("${NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT:}")String entitlement){return new PricingAuthority(jdbc,Clock.systemUTC(),phase,entitlement);}
 @Bean Pricing pricing(JdbcTemplate jdbc,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new JdbcPricing(jdbc,phase);}
 @Bean FrozenPrices frozenPrices(JdbcTemplate jdbc,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new JdbcFrozenPrices(jdbc,phase);}
 @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(PriceAdjustments.class) PriceAdjustments priceAdjustments(JdbcTemplate jdbc,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new Bp1PriceAdjustments(jdbc,phase);}
 @Bean PricingProviders pricingProviders(@Value("${NIGSON_PRICING_CHECKOUT_ENABLED:false}")boolean enabled,@Value("${APP_ENV}")String environment,@Value("${NIGSON_PRICING_PROVIDER_MODE:}")String mode,@Value("${NIGSON_PRICING_PAYMENT_APPROVAL:}")String payment,@Value("${NIGSON_PRICING_CARRIER_APPROVAL:}")String carrier,@Value("${NIGSON_PRICING_PROVIDER_APPROVAL_UNTIL:}")String until,@Value("${NIGSON_CARRIER_ACCOUNT_FINGERPRINT:}")String accountHash){return new PricingProviders(enabled,environment,mode,payment,carrier,until.isEmpty()?null:com.nigson.shared.problem.ApiValues.instant(until),Clock.systemUTC(),accountHash);}
 @Bean PriceBookService priceBookService(JdbcTemplate jdbc,PricingAuthority authority,PlatformStore commands,AuditWriter audit,org.springframework.transaction.PlatformTransactionManager manager,io.micrometer.core.instrument.MeterRegistry metrics){var clock=Clock.systemUTC();return new PriceBookService(new JdbcPriceBookRepository(jdbc,clock),authority,commands,audit,clock,jdbc,manager,metrics);}
 @Bean PriceQueryService priceQueryService(JdbcTemplate jdbc,Pricing pricing,PricingAuthority authority,org.springframework.transaction.PlatformTransactionManager manager,@Value("${NIGSON_PRICING_QUOTE_TTL_SECONDS:300}")int ttl,@Value("${NIGSON_PRICING_READ_BUDGET_PER_SECOND:1000}")int budget){return new PriceQueryService(pricing,authority,jdbc,Clock.systemUTC(),manager,ttl,budget);}
 @Bean JdbcCheckoutQuoteRepository checkoutQuoteRepository(JdbcTemplate jdbc,Pricing pricing,MarketEligibility markets,PricingProviders providers,PriceAdjustments adjustments,FrozenPrices frozen,PricingAuthority authority,@Value("${NIGSON_PRICING_QUOTE_TTL_SECONDS:300}")int ttl){return new JdbcCheckoutQuoteRepository(jdbc,Clock.systemUTC(),pricing,markets,providers,adjustments,frozen,authority,ttl);}
 @Bean CheckoutQuoteService checkoutQuoteService(JdbcCheckoutQuoteRepository repo,PricingAuthority authority,PlatformStore commands,AuditWriter audit,JdbcTemplate jdbc,io.micrometer.core.instrument.MeterRegistry metrics){return new CheckoutQuoteService(repo,authority,commands,audit,jdbc,metrics);}
}
