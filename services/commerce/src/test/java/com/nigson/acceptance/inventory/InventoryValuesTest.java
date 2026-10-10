package com.nigson.acceptance.inventory;
import com.nigson.commerce.inventory.*;
import com.nigson.shared.events.EventCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class InventoryValuesTest {
 @Test void quantitiesAreSignedClosedStringsAndConformToImmutableSkuScale(){assertThat(InventoryValues.quantity("-1.25",2,true).toPlainString()).isEqualTo("-1.25");for(String input:List.of("0","-0","1e3","+1"," 1","01","1.0001","100000000000000000","NaN"))assertThatThrownBy(()->InventoryValues.quantity(input,3,true)).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->InventoryValues.quantity("1.001",2,true)).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->InventoryValues.quantity("-1",0,false)).hasMessage("VALIDATION_ERROR");}
 @Test void locationAndAdjustmentInputsRejectExtraFieldsControlAndUntrustedOwnership(){var input=EventCodec.JSON.readTree("{\"code\":\"SYNTHETIC\",\"name\":\"Synthetic offline location\"}");assertThat(InventoryValues.location(input).code()).isEqualTo("SYNTHETIC");for(String extra:List.of("active","version","partyId")){var changed=(tools.jackson.databind.node.ObjectNode)input.deepCopy();changed.put(extra,"untrusted");assertThatThrownBy(()->InventoryValues.location(changed)).hasMessage("VALIDATION_ERROR");}var bad=(tools.jackson.databind.node.ObjectNode)input.deepCopy();bad.put("name","\nSynthetic location");assertThatThrownBy(()->InventoryValues.location(bad)).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new InventoryValues.Query(null,null,0,101,"skuId,asc")).hasMessage("VALIDATION_ERROR");}
 @Test void stockMetricLabelsRemainClosed(){var metrics=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();metrics.config().meterFilter(new com.nigson.commerce.platform.MetricPolicy());metrics.counter("nigson.inventory.movements","movement_type","ADJUST").increment();assertThat(metrics.find("nigson.inventory.movements").tag("movement_type","ADJUST").counter().count()).isEqualTo(1);metrics.counter("nigson.inventory.movements","movement_type",UUID.randomUUID().toString()).increment();assertThat(metrics.getMeters()).hasSize(1);}
}
