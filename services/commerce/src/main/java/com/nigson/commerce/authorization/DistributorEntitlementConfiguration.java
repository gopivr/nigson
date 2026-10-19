package com.nigson.commerce.authorization;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.*;

@Configuration(proxyBeanMethods=false)
public class DistributorEntitlementConfiguration {
 @Bean DistributorEntitlementService distributorEntitlementService(JdbcTemplate jdbc,PlatformStore commands,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,@Value("${NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT:}")String entitlement){return new DistributorEntitlementService(jdbc,commands,audit,metrics,Clock.systemUTC(),phase,entitlement);}
}
