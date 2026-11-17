package com.nigson.commerce.payment;

import java.util.*;

/**
 * Local/test settlement verifies provider truth without refund credentials. Refund execution then
 * fails closed with an explicit error rather than inventing a merchant integration id; production
 * still refuses to start without the real one.
 */
public final class SettlementOnlyRefunds implements RefundOperations {
 private final String environment;
 public SettlementOnlyRefunds(String environment){
  if(!Set.of("local","test").contains(environment))throw new IllegalArgumentException("PAYSTACK_REFUND_ACCOUNT_REQUIRED");
  this.environment=environment;
 }
 @Override public void submit(UUID refund,UUID correlation){throw new IllegalStateException("PAYSTACK_REFUND_ACCOUNT_REQUIRED:"+environment);}
 @Override public void inspect(UUID refund,UUID correlation){throw new IllegalStateException("PAYSTACK_REFUND_ACCOUNT_REQUIRED:"+environment);}
}
