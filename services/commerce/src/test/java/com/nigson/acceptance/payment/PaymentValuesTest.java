package com.nigson.acceptance.payment;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.nigson.commerce.payment.PaymentValues;
import com.nigson.shared.events.EventCodec;

class PaymentValuesTest {
 @Test void refundValuesUseExactMinorUnitsAndBoundDistinctLines(){
  UUID line=UUID.randomUUID();var body=EventCodec.JSON.readTree("{\"amount\":\"12.30\",\"reason\":\"Synthetic refund request\",\"lines\":[{\"orderLineId\":\""+line+"\",\"quantity\":\"2.000\",\"amount\":\"12.30\"}]}");
  var input=PaymentValues.refund(body);assertThat(input.amount()).isEqualTo("12.30");assertThat(input.lines().getFirst().quantity()).isEqualTo("2");
  for(String amount:List.of("12.300","0","-1.00","1e2")){var invalid=body.deepCopy();((tools.jackson.databind.node.ObjectNode)invalid).put("amount",amount);assertThatThrownBy(()->PaymentValues.refund(invalid)).hasMessage("VALIDATION_ERROR");}
  assertThatThrownBy(()->new PaymentValues.RefundInput("12.30","Synthetic refund request",List.of(input.lines().getFirst(),input.lines().getFirst()))).hasMessage("VALIDATION_ERROR");
  assertThatThrownBy(()->new PaymentValues.RefundInput("12.30","Synthetic refund request",Collections.nCopies(101,input.lines().getFirst()))).hasMessage("VALIDATION_ERROR");
  assertThatThrownBy(()->PaymentValues.refund(EventCodec.JSON.readTree("{\"amount\":\"1.00\",\"reason\":\"Synthetic refund request\",\"lines\":null}"))).hasMessage("VALIDATION_ERROR");
 }
 @Test void approvalAndReconciliationAreClosedAndStrictlyTyped(){
  assertThat(PaymentValues.approval(EventCodec.JSON.readTree("{\"approve\":false,\"reason\":\"Synthetic rejected request\"}")).approve()).isFalse();
  assertThatThrownBy(()->PaymentValues.approval(EventCodec.JSON.readTree("{\"approve\":\"false\",\"reason\":\"Synthetic rejected request\"}"))).hasMessage("VALIDATION_ERROR");
  UUID job=UUID.randomUUID();assertThat(PaymentValues.reconciliation(EventCodec.JSON.readTree("{\"jobId\":\""+job+"\",\"limit\":100}")).limit()).isEqualTo(100);
  for(String limit:List.of("0","101","1.0","\"10\""))assertThatThrownBy(()->PaymentValues.reconciliation(EventCodec.JSON.readTree("{\"jobId\":\""+job+"\",\"limit\":"+limit+"}"))).hasMessage("VALIDATION_ERROR");
  assertThatThrownBy(()->PaymentValues.refund(EventCodec.JSON.readTree("{\"amount\":\"1.00\",\"reason\":\"Synthetic refund request\",\"approved\":true}"))).hasMessage("VALIDATION_ERROR");
 }
}
