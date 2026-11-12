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
 @Test void quoteInputSupportsPandaGoDestinationCoordinatesWithoutAFabricatedShipper(){
  var tree=(tools.jackson.databind.node.ObjectNode)EventCodec.JSON.valueToTree(input());tree.putNull("shipper");var destination=(tools.jackson.databind.node.ObjectNode)tree.path("destination");destination.put("latitude",6.5244);destination.put("longitude",3.3792);tree.remove("serviceCode");
  var parsed=CarrierValues.quoteInput(tree);assertThat(parsed.shipper()).isNull();assertThat(parsed.destination().latitude()).isEqualByComparingTo("6.5244");assertThat(parsed.destination().longitude()).isEqualByComparingTo("3.3792");
  destination.putNull("longitude");assertThatThrownBy(()->CarrierValues.quoteInput(tree)).isInstanceOf(Problem.Failure.class);
 }
 private CarrierValues.QuoteInput inputWithIds(tools.jackson.databind.JsonNode tree){return new CarrierValues.QuoteInput(UUID.fromString(tree.path("commandId").asString()),UUID.fromString(tree.path("cartId").asString()),"NG","NGN",address(),address(),List.of(parcel()),"SYNTHETIC_SERVICE");}


}
