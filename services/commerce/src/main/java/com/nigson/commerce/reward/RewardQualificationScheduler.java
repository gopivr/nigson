package com.nigson.commerce.reward;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
/** Releases referral qualification holds from persisted Commerce timestamps. */
public final class RewardQualificationScheduler {
 private final RewardOperations rewards;private final TransactionTemplate tx;
 public RewardQualificationScheduler(RewardOperations rewards,org.springframework.transaction.PlatformTransactionManager manager){this.rewards=rewards;this.tx=new TransactionTemplate(manager);this.tx.setTimeout(5);}
 @Scheduled(fixedDelayString="${NIGSON_REWARD_QUALIFICATION_INTERVAL_MS:30000}") public void run(){tx.executeWithoutResult(status->rewards.qualifyDue());}
}
