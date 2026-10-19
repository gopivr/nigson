package com.nigson.experience.engagement;

import com.nigson.experience.platform.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
public class EngagementConfiguration {
 @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="NIGSON_PURCHASE_PROOF_ENABLED",havingValue="true") PurchaseProofClient purchaseProof(Environment e){return PurchaseProofClient.from(e);}
 @Bean EngagementService engagementService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,org.springframework.beans.factory.ObjectProvider<PurchaseProofClient> proof){var client=proof.getIfAvailable();return new EngagementService(jdbc,store,audit,client,client,Clock.systemUTC());}
}
