package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.integration.platform.*;
import com.nigson.integration.providers.*;
import com.nigson.shared.security.ProviderHttp;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_CARRIER_ENABLED",havingValue="true")
public class CarrierConfiguration {
 @Bean CarrierProvider carrierProvider(Environment env){if(!env.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_PRIVATE_SECURITY_REQUIRED");return new FedExAdapter(env.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(env.getRequiredProperty("FEDEX_MODE")),env.getRequiredProperty("FEDEX_BASE_URL"),env.getRequiredProperty("FEDEX_CLIENT_ID"),env.getRequiredProperty("FEDEX_CLIENT_SECRET"),env.getRequiredProperty("FEDEX_ACCOUNT_NUMBER"));}
 @Bean CarrierQuoteService carrierQuoteService(CarrierProvider provider,PlatformStore store,AuditWriter audit,Environment env){return new CarrierQuoteService(provider,store,audit,Clock.systemUTC(),Set.of(env.getRequiredProperty("NIGSON_CARRIER_SERVICES").split(",",-1)),env.getRequiredProperty("FEDEX_MODE").equals("TEST")?"SANDBOX":env.getRequiredProperty("FEDEX_MODE"),env.getRequiredProperty("NIGSON_CARRIER_DEFAULT_SERVICE"),env.getRequiredProperty("NIGSON_CARRIER_PICKUP_TYPE"),env.getRequiredProperty("NIGSON_CARRIER_APPROVAL"),Instant.parse(env.getRequiredProperty("NIGSON_CARRIER_APPROVAL_UNTIL")));}
 @Bean
 @ConditionalOnProperty(name="NIGSON_CARRIER_BOOKING_INTAKE_ENABLED",havingValue="true")
 CarrierBookingRequestedHandler carrierBookingRequestedHandler(CarrierProvider provider,com.nigson.integration.provider.JobRepository jobs,AuditWriter audit){return new CarrierBookingRequestedHandler(jobs,audit,Clock.systemUTC(),provider.mode());}
}
