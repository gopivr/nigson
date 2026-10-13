package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CarrierValuesTest {
 private final UUID id=UUID.randomUUID();
 private CarrierValues.Address address(){return new CarrierValues.Address("Synthetic Recipient","+2348000000001","Synthetic street",null,"Lagos","LA",null,"NG");}
 private CarrierValues.Parcel parcel(){return new CarrierValues.Parcel(id,"2","1.2","12.01","13","14");}
 private CarrierValues.QuoteInput input(){return new CarrierValues.QuoteInput(UUID.randomUUID(),UUID.randomUUID(),"NG","NGN",address(),address(),List.of(parcel()),"SYNTHETIC_SERVICE");}
 @Test void closedProtocolRejectsAccountsWrongCountryAndOversizedArrays(){
  var tree=(tools.jackson.databind.node.ObjectNode)EventCodec.JSON.valueToTree(input());assertThat(CarrierValues.quoteInput(tree)).isEqualTo(inputWithIds(tree));tree.put("accountNumber","attacker");assertThatThrownBy(()->CarrierValues.quoteInput(tree)).isInstanceOf(Problem.Failure.class);tree.remove("accountNumber");tree.put("currency","USD");assertThatThrownBy(()->CarrierValues.quoteInput(tree)).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->new CarrierValues.QuoteInput(id,id,"NG","NGN",address(),address(),Collections.nCopies(101,parcel()),null)).isInstanceOf(Problem.Failure.class);
  assertThatThrownBy(()->new CarrierValues.Parcel(id,"1","0","1","1","1")).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->new CarrierValues.Parcel(id,"1","1","0","1","1")).isInstanceOf(Problem.Failure.class);
  assertThat(address().toString()).doesNotContain("+234","street","Lagos");assertThat(input().toString()).doesNotContain("+234","street");
 }
 private CarrierValues.QuoteInput inputWithIds(tools.jackson.databind.JsonNode tree){return new CarrierValues.QuoteInput(UUID.fromString(tree.path("commandId").asString()),UUID.fromString(tree.path("cartId").asString()),"NG","NGN",address(),address(),List.of(parcel()),"SYNTHETIC_SERVICE");}
 @Test void rateRequestKeepsExactWeightAndConservativelyRoundsCartons(){var request=CarrierValues.rateRequest(input(),"SYNTHETIC_SERVICE","DROPOFF_AT_FEDEX_LOCATION");assertThat(request.has("accountNumber")).isFalse();var shipment=request.path("requestedShipment");assertThat(shipment.path("rateRequestType").path(0).asString()).isEqualTo("ACCOUNT");var box=shipment.path("requestedPackageLineItems").path(0);assertThat(box.path("weight").path("value").decimalValue()).isEqualByComparingTo("1.2");assertThat(box.path("dimensions").path("length").asInt()).isEqualTo(13);assertThat(shipment.path("shipper").path("address").has("postalCode")).isFalse();assertThatThrownBy(()->CarrierValues.rateRequest(input(),"SYNTHETIC_SERVICE","invented")).isInstanceOf(Problem.Failure.class);}
 private tools.jackson.databind.JsonNode rate(String currency,String amount){return EventCodec.JSON.readTree("{\"rateReplyDetails\":[{\"serviceType\":\"SYNTHETIC_SERVICE\",\"ratedShipmentDetails\":[{\"rateType\":\"ACCOUNT\",\"totalNetCharge\":"+amount+",\"shipmentRateDetail\":{\"currency\":\""+currency+"\"}}]}]}");}
 @Test void rateRequiresOneMatchingAccountServiceAndExactNgn(){var expires=Instant.now().plusSeconds(300);assertThat(CarrierValues.rate(rate("NGN","1234.56"),"SYNTHETIC_SERVICE",expires).amount()).isEqualTo("1234.56");assertThat(CarrierValues.rate(rate("NGN","0"),"SYNTHETIC_SERVICE",expires).amount()).isEqualTo("0.00");assertThatThrownBy(()->CarrierValues.rate(rate("USD","1234.56"),"SYNTHETIC_SERVICE",expires)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->CarrierValues.rate(rate("NGN","1234.567"),"SYNTHETIC_SERVICE",expires)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->CarrierValues.rate(rate("NGN","-1"),"SYNTHETIC_SERVICE",expires)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->CarrierValues.rate(rate("NGN","1234.56"),"ANOTHER_SERVICE",expires)).isInstanceOf(Problem.Failure.class);var duplicate=rate("NGN","1");((tools.jackson.databind.node.ArrayNode)duplicate.path("rateReplyDetails")).add(duplicate.path("rateReplyDetails").path(0).deepCopy());assertThatThrownBy(()->CarrierValues.rate(duplicate,"SYNTHETIC_SERVICE",expires)).isInstanceOf(Problem.Failure.class);}
}
