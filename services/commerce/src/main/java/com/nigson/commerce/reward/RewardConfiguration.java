package com.nigson.commerce.reward;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.commerce.platform.*;
@Configuration(proxyBeanMethods=false)
public class RewardConfiguration {
 @Bean RewardService rewardService(JdbcTemplate jdbc,PlatformStore commands,AuditWriter audit,io.micrometer.core.instrument.MeterRegistry metrics,@Value("${NIGSON_REWARD_REFERRAL_BASE_URL:}")String base,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,@Value("${NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT:}")String entitlement){return new RewardService(jdbc,commands,audit,Clock.systemUTC(),base,phase,metrics,entitlement);}
 @Bean com.nigson.commerce.affiliate.AffiliateCommissions affiliateCommissions(JdbcTemplate jdbc,AuditWriter audit,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,org.springframework.transaction.PlatformTransactionManager manager){return new com.nigson.commerce.affiliate.AffiliateCommissions(jdbc,audit,Clock.systemUTC(),phase,manager);}
 @Bean public RewardOperations rewardOperations(JdbcTemplate jdbc,AuditWriter audit,com.nigson.commerce.affiliate.AffiliateCommissions affiliate){var rewards=new JdbcRewardOperations(jdbc,audit,Clock.systemUTC());return new RewardOperations(){public void orderSettled(java.util.UUID order,java.util.UUID correlation){rewards.orderSettled(order,correlation);affiliate.orderSettled(order,correlation);}public void refundSettled(java.util.UUID refund,java.util.UUID correlation){rewards.refundSettled(refund,correlation);affiliate.refundSettled(refund,correlation);}public void qualifyDue(){rewards.qualifyDue();}};}
 @Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${NIGSON_PRODUCT_PHASE:1} >= 2")
 @org.springframework.scheduling.annotation.EnableScheduling
 static class QualificationSchedule {
  @Bean RewardQualificationScheduler rewardQualificationScheduler(RewardOperations rewards,org.springframework.transaction.PlatformTransactionManager manager){return new RewardQualificationScheduler(rewards,manager);}
 }
}
