package com.nigson.commerce.fulfillment;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.*;
import com.nigson.commerce.market.MarketEligibility;
import com.nigson.commerce.security.*;
import com.nigson.shared.events.EventCodec;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_CARRIER_QUOTES_ENABLED",havingValue="true")
public class CarrierQuoteConfiguration {
 @Bean CarrierRates carrierRates(Environment env,WorkloadCredentials credentials){if(!env.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_SOURCE_SECURITY_REQUIRED");String provider=env.getProperty("NIGSON_CARRIER_PROVIDER","FEDEX");return new HttpCarrierRates(env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token,Clock.systemUTC(),env.getRequiredProperty("NIGSON_FULFILLMENT_MODE"),env.getRequiredProperty("NIGSON_CARRIER_ACCOUNT_FINGERPRINT"),provider);}
 @Bean DeliveryQuoteService deliveryQuoteService(JdbcTemplate jdbc,PlatformTransactionManager manager,PricingAuthority authority,PricingProviders providers,MarketEligibility markets,FulfillmentPolicy fulfillment,CarrierRates rates,ActorDelegation delegation,PlatformStore commands,AuditWriter audit,Pricing pricing,FrozenPrices frozen,Environment env){String provider=env.getProperty("NIGSON_CARRIER_PROVIDER","FEDEX");var origin=provider.equals("FEDEX")?DeliveryQuoteValues.origin(EventCodec.JSON.readTree(env.getRequiredProperty("NIGSON_CARRIER_ORIGIN_JSON"))):null;return new DeliveryQuoteService(jdbc,manager,authority,providers,markets,fulfillment,rates,delegation,commands,audit,Clock.systemUTC(),origin,env.getRequiredProperty("NIGSON_FULFILLMENT_MODE"),provider,pricing,frozen);}
}
