package com.nigson.acceptance.order;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.Instant;
import com.nigson.commerce.order.OrderValues;
import com.nigson.shared.events.EventCodec;
class OrderValuesTest {
 @Test void checkoutAcceptsOnlyServerQuoteHashAndHostedMethod(){String body="{\"checkoutQuoteId\":\""+UUID.randomUUID()+"\",\"quoteHash\":\""+"a".repeat(64)+"\",\"paymentMethod\":\"HOSTED\"}";assertThat(OrderValues.checkout(EventCodec.JSON.readTree(body)).paymentMethod()).isEqualTo("HOSTED");for(String invalid:List.of(body.replace("HOSTED","CARD"),body.replace("a".repeat(64),"A".repeat(64)),body.replace("}",",\"amount\":\"0.00\"}"),body.replace("}",",\"partyId\":null}")))assertThatThrownBy(()->OrderValues.checkout(EventCodec.JSON.readTree(invalid))).hasMessage("VALIDATION_ERROR");}
 @Test void reasonProofAndQueriesRemainClosed(){assertThat(OrderValues.reason(EventCodec.JSON.readTree("{\"reason\":\"Customer requests cancellation\"}"))).hasSize(30);assertThatThrownBy(()->OrderValues.reason(EventCodec.JSON.readTree("{\"reason\":\" padded customer reason \"}"))).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->OrderValues.proof(EventCodec.JSON.readTree("{\"partyId\":null,\"orderLineId\":null,\"productId\":null}"))).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new OrderValues.Query(null,"UNKNOWN",null,null,null,0,20,"createdAt,desc")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new OrderValues.Query(null,null,null,null,null,0,101,"createdAt,desc")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new OrderValues.Query(null,null,null,Instant.now(),Instant.EPOCH,0,20,"createdAt,desc")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new OrderValues.Query(null,null,null,null,null,0,20,"partyId,desc")).hasMessage("VALIDATION_ERROR");}
}
