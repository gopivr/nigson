package com.nigson.commerce.payment;

import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
import com.nigson.shared.security.ProviderHttp;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_PROVIDERS_ENABLED",havingValue="true")
public class ProviderConfiguration {
 @Bean PaystackAdapter paystack(@Value("${APP_ENV}") String environment,@Value("${PAYSTACK_MODE}") String mode,@Value("${PAYSTACK_BASE_URL}") String endpoint,@Value("${PAYSTACK_SECRET_KEY}") String key){return new PaystackAdapter(environment,ProviderHttp.Mode.valueOf(mode),endpoint,key);}
}
