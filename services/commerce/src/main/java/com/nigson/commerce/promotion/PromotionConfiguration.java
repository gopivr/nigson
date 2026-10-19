package com.nigson.commerce.promotion;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.PricingAuthority;
@Configuration(proxyBeanMethods=false)
public class PromotionConfiguration {
 @Bean PromotionService promotionService(JdbcTemplate jdbc,PlatformStore commands,PricingAuthority authority,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new PromotionService(jdbc,commands,authority,audit,Clock.systemUTC(),metrics,phase);}
}
