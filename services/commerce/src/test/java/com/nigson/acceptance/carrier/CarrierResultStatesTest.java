package com.nigson.acceptance.carrier;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import com.nigson.commerce.fulfillment.*;
import static org.assertj.core.api.Assertions.*;
class CarrierResultStatesTest {
 private CarrierResultValues.Result tracking(String state){return new CarrierResultValues.Result(UUID.randomUUID(),"FEDEX","123456789012",state,Instant.now(),null,null,null,null,"MOCK");}
 @Test void providerTransitCannotDispatchAndDeliveryRequiresActualHandover(){assertThat(CarrierResultStates.next("READY",tracking("SHIPPED"),false,true)).isEqualTo("READY");assertThatThrownBy(()->CarrierResultStates.next("READY",tracking("DELIVERED"),false,true)).hasMessage("INVALID_TRANSITION");assertThat(CarrierResultStates.next("SHIPPED",tracking("DELIVERED"),false,true)).isEqualTo("DELIVERED");}
 @Test void uncertaintyAndOlderEventsCannotRegressPhysicalTruth(){var unknown=new CarrierResultValues.Result(UUID.randomUUID(),"FEDEX",UUID.randomUUID().toString(),"EXCEPTION",Instant.now(),null,null,null,"UNKNOWN","MOCK");assertThat(CarrierResultStates.next("CREATED",unknown,false,false)).isEqualTo("EXCEPTION");assertThat(CarrierResultStates.next("SHIPPED",unknown,false,true)).isEqualTo("SHIPPED");assertThat(CarrierResultStates.next("SHIPPED",tracking("EXCEPTION"),false,true)).isEqualTo("SHIPPED");assertThat(CarrierResultStates.next("DELIVERED",tracking("SHIPPED"),true,true)).isEqualTo("DELIVERED");assertThatThrownBy(()->CarrierResultStates.next("SHIPPED",tracking("CANCELLED"),false,true)).hasMessage("INVALID_TRANSITION");}
}
