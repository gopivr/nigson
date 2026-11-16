package com.nigson.commerce.payment;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Local settlement never invents a merchant integration; refunds fail closed instead. */
class SettlementOnlyRefundsTest {
 @Test void localAndTestMaySettleWithoutExecutingRefunds(){
  for(String environment:new String[]{"local","test"}){
   RefundOperations refunds=new SettlementOnlyRefunds(environment);
   assertThatThrownBy(()->refunds.submit(UUID.randomUUID(),UUID.randomUUID())).hasMessageContaining("PAYSTACK_REFUND_ACCOUNT_REQUIRED");
   assertThatThrownBy(()->refunds.inspect(UUID.randomUUID(),UUID.randomUUID())).hasMessageContaining("PAYSTACK_REFUND_ACCOUNT_REQUIRED");
  }
 }
 @Test void everyOtherEnvironmentStillRequiresTheRealIntegration(){
  for(String environment:new String[]{"dev","qa","stage","prod"})
   assertThatThrownBy(()->new SettlementOnlyRefunds(environment)).hasMessage("PAYSTACK_REFUND_ACCOUNT_REQUIRED");
 }
}
