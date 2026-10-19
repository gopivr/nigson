package com.nigson.experience.lead;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.experience.platform.*;

@Configuration(proxyBeanMethods=false)
public class QuoteLeadConfiguration {
 @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="NIGSON_QUOTE_CATALOGUE_MODE",havingValue="http") QuoteLeadAssociation.SkuEligibilityPort quoteSkuEligibilityHttp(Environment e){return HttpQuoteSkuEligibilityClient.from(e);}
 @Bean QuoteLeadAssociation quoteLeadAssociation(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,Environment e,ObjectProvider<QuoteLeadAssociation.SkuEligibilityPort> skus){String recipient=e.getProperty("NIGSON_LEAD_RECIPIENT_GROUP");if(recipient!=null&&!recipient.isBlank()&&!recipient.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))throw new IllegalArgumentException("INVALID_LEAD_RECIPIENT_GROUP_CONFIGURATION");return new QuoteLeadAssociation(jdbc,store,audit,java.time.Clock.systemUTC(),skus.getIfAvailable(),recipient);}
}
