package com.nigson.acceptance.carrier;
import java.util.*;
import java.time.*;
import com.nigson.commerce.fulfillment.CarrierResultValues;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CarrierResultValuesTest {
 private Map<String,Object> base(){return new HashMap<>(Map.of("commandId",UUID.randomUUID().toString(),"provider","FEDEX","reference","123456789012","status","SHIPPED","occurredAt",Instant.now().toString(),"mode","MOCK"));}
 @Test void closedProviderInputCannotInjectPriceAccountDispatchEvidenceOrUrlFetch(){var b=base();assertThat(CarrierResultValues.result(EventCodec.JSON.valueToTree(b)).toString()).isEqualTo("CarrierResult[protected]");for(String field:List.of("account","fee","dispatchedAt","providerUrl")){var invalid=new HashMap<>(b);invalid.put(field,"unsafe");assertThatThrownBy(()->CarrierResultValues.result(EventCodec.JSON.valueToTree(invalid))).hasMessage("VALIDATION_ERROR");}b.put("trackingUrl","http://provider.invalid/private");assertThatThrownBy(()->CarrierResultValues.result(EventCodec.JSON.valueToTree(b))).hasMessage("VALIDATION_ERROR");}
 @Test void readyRequiresBookedTrustedLabelPairAndUnknownCannotPretendBooking(){var b=base();b.put("status","READY");assertThatThrownBy(()->CarrierResultValues.result(EventCodec.JSON.valueToTree(b))).hasMessage("FULFILLMENT_RULE_VIOLATION");b.put("bookingState","BOOKED");b.put("labelObjectKey","test/commerce/shipping_label/"+UUID.randomUUID()+"/0/pdf");b.put("labelChecksum","a".repeat(64));assertThat(CarrierResultValues.result(EventCodec.JSON.valueToTree(b)).bookingState()).isEqualTo("BOOKED");b.put("bookingState","UNKNOWN");assertThatThrownBy(()->CarrierResultValues.result(EventCodec.JSON.valueToTree(b))).hasMessage("FULFILLMENT_RULE_VIOLATION");b.remove("labelObjectKey");b.remove("labelChecksum");b.put("status","EXCEPTION");assertThat(CarrierResultValues.result(EventCodec.JSON.valueToTree(b)).bookingState()).isEqualTo("UNKNOWN");}
}
