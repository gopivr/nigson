package com.nigson.commerce.reward;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
public class RewardConfiguration {
 @Bean RewardService rewardService(JdbcTemplate jdbc,PlatformStore commands,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,@Value("${NIGSON_REWARD_REFERRAL_BASE_URL:}")String base,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,@Value("${NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT:}")String entitlement){return new RewardService(jdbc,commands,audit,Clock.systemUTC(),base,phase,metrics,entitlement);}
 @Bean RewardOperations rewardOperations(JdbcTemplate jdbc,AuditWriter audit){return new JdbcRewardOperations(jdbc,audit,Clock.systemUTC());}
 @Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${NIGSON_PRODUCT_PHASE:1} >= 2")
 @org.springframework.scheduling.annotation.EnableScheduling
 static class QualificationSchedule {
  @Bean RewardQualificationScheduler rewardQualificationScheduler(RewardOperations rewards,org.springframework.transaction.PlatformTransactionManager manager){return new RewardQualificationScheduler(rewards,manager);}
 }
}
