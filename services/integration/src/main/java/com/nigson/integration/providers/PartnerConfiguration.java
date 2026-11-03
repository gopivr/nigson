package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

/** Explicit opt-in: missing partner/account data fails startup; no mock fallback. */
@Configuration(proxyBeanMethods=false)
public class PartnerConfiguration {
 @Bean @ConditionalOnProperty(name="NIGSON_TERMII_ENABLED",havingValue="true")
 TermiiTokenAdapter termiiToken(Environment e){return new TermiiTokenAdapter(e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(e.getRequiredProperty("TERMII_MODE")),e.getRequiredProperty("TERMII_BASE_URL"),e.getRequiredProperty("TERMII_API_KEY"),e.getRequiredProperty("TERMII_SENDER_ID"));}
 @Bean @ConditionalOnProperty(name="NIGSON_DELLYMAN_ENABLED",havingValue="true")
 DellymanAdapter dellyman(Environment e){return new DellymanAdapter(e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(e.getRequiredProperty("DELLYMAN_MODE")),e.getRequiredProperty("DELLYMAN_BASE_URL"),e.getRequiredProperty("DELLYMAN_API_KEY"),e.getRequiredProperty("DELLYMAN_WEBHOOK_SECRET"));}
}
