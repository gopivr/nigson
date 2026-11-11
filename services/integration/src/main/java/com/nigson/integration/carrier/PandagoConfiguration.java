package com.nigson.integration.carrier;

import com.nigson.integration.providers.PandagoAdapter;
import com.nigson.integration.platform.PlatformStore;
import com.nigson.integration.platform.AuditWriter;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

/** Carrier orchestration depends on provider adapters, never the reverse. */
@Configuration(proxyBeanMethods=false)
public class PandagoConfiguration {
 @Bean @ConditionalOnProperty(name="NIGSON_PANDAGO_ENABLED",havingValue="true")
 PandagoQuoteService pandagoQuoteService(PandagoAdapter provider,PlatformStore store,AuditWriter audit,Environment e,com.nigson.integration.providers.GeocodingProvider geocoder){
  return new PandagoQuoteService(provider,store,audit,java.time.Clock.systemUTC(),e.getRequiredProperty("NIGSON_PANDAGO_APPROVAL"),java.time.Instant.parse(e.getRequiredProperty("NIGSON_PANDAGO_APPROVAL_UNTIL")),geocoder);
 }
}
