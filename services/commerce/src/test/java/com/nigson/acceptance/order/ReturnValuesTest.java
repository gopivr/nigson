package com.nigson.acceptance.order;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.nigson.commerce.order.ReturnValues;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

class ReturnValuesTest {
 private static final UUID LINE=UUID.fromString("00000000-0000-0000-0000-000000000001");

 @Test void requestsAreClosedScaledAndBounded(){
  String body="{\"orderLineId\":\""+LINE+"\",\"quantity\":\"2.000\",\"reason\":\"Synthetic damaged product return\"}";
  var request=ReturnValues.request(EventCodec.JSON.readTree(body));
  assertThat(request.orderLineId()).isEqualTo(LINE);
  assertThat(request.quantity()).isEqualTo("2");
  for(String invalid:List.of(body.replace("2.000","0"),body.replace("\"Synthetic damaged product return\"","\"short\""),body.replace("}",",\"customerId\":null}"))){
   assertThatThrownBy(()->ReturnValues.request(EventCodec.JSON.readTree(invalid))).hasMessage("VALIDATION_ERROR");
  }
 }

 @Test void reviewRequiresInstructionsOnApprovalAndRejectsThemOnRejection(){
  String approve="{\"approve\":true,\"reason\":\"Synthetic policy review\",\"instructions\":\"Return through the approved support channel\"}";
  assertThat(ReturnValues.review(EventCodec.JSON.readTree(approve)).instructions()).contains("support");
  assertThatThrownBy(()->ReturnValues.review(EventCodec.JSON.readTree(approve.replace(",\"instructions\":\"Return through the approved support channel\"","")))).hasMessage("VALIDATION_ERROR");
  String reject="{\"approve\":false,\"reason\":\"Synthetic policy review\"}";
  assertThat(ReturnValues.review(EventCodec.JSON.readTree(reject)).approve()).isFalse();
  assertThatThrownBy(()->ReturnValues.review(EventCodec.JSON.readTree(reject.replace("}",",\"instructions\":\"Not allowed for rejection\"}")))).hasMessage("VALIDATION_ERROR");
  assertThatThrownBy(()->ReturnValues.review(EventCodec.JSON.readTree(approve.replace("}",",\"state\":\"RECEIVED\"}")))).hasMessage("VALIDATION_ERROR");
 }

 @Test void receiptAndRefundRejectUnsafeReferencesAndAmounts(){
  String receipt="{\"receiptReference\":\"SYNTH_RECEIPT_01\",\"restock\":true,\"reason\":\"Synthetic physical receipt inspection\"}";
  assertThat(ReturnValues.receipt(EventCodec.JSON.readTree(receipt)).restock()).isTrue();
  for(String invalid:List.of(receipt.replace("SYNTH_RECEIPT_01","../receipt"),receipt.replace("true","\"true\""),receipt.replace("}",",\"receivedBy\":null}"))){
   assertThatThrownBy(()->ReturnValues.receipt(EventCodec.JSON.readTree(invalid))).hasMessage("VALIDATION_ERROR");
  }
  String refund="{\"amount\":\"10.00\",\"reason\":\"Synthetic approved return refund\"}";
  assertThat(ReturnValues.refund(EventCodec.JSON.readTree(refund)).amount()).isEqualTo("10.00");
  for(String invalid:List.of(refund.replace("10.00","0.00"),refund.replace("10.00","10.001"),refund.replace("10.00","-1.00"),refund.replace("}",",\"orderLineId\":null}"))){
   assertThatThrownBy(()->ReturnValues.refund(EventCodec.JSON.readTree(invalid))).hasMessage("VALIDATION_ERROR");
  }
 }
}
