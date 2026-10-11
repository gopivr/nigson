package com.nigson.acceptance.cart;
import com.nigson.commerce.cart.CartValues;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CartValuesTest {
 @Test void cartCreationCannotSupplyActorStateVersionOrNilOrganization(){UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");assertThat(CartValues.create(EventCodec.JSON.readTree("{\"marketId\":\""+id+"\"}")).organizationId()).isNull();for(String extra:List.of("\"partyId\":\""+id+"\"","\"state\":\"ACTIVE\"","\"version\":0","\"organizationId\":null","\"organizationId\":\"00000000-0000-0000-0000-000000000000\""))assertThatThrownBy(()->CartValues.create(EventCodec.JSON.readTree("{\"marketId\":\""+id+"\","+extra+"}"))).hasMessage("VALIDATION_ERROR");}
 @Test void setRequiresPositiveExactBoundedQuantityAndRemovalIsExplicit(){for(String value:List.of("0","0.000","-1","1e2","1.0000"," 1","1 ","100000000000000000"))assertThatThrownBy(()->CartValues.quantity(EventCodec.JSON.valueToTree(Map.of("quantity",value)))).hasMessage("VALIDATION_ERROR");assertThat(CartValues.quantity(EventCodec.JSON.valueToTree(Map.of("quantity","1.000"))).quantity()).isEqualTo("1");assertThatThrownBy(()->CartValues.quantity(EventCodec.JSON.valueToTree(Map.of("quantity","1","price","0")))).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->CartValues.version(Long.MAX_VALUE)).hasMessage("VALIDATION_ERROR");}
}
