package com.nigson.acceptance.party;
import com.nigson.commerce.party.*;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PartyValuesTest {
 @Test void exactInputTypesAndImmutableIdentityFields(){
  for(String body:new String[]{"null","[]","{}","{\"id\":\"x\"}","{\"displayName\":false}","{\"locale\":null}","{\"phone\":[]}"})
   assertThatThrownBy(()->PartyValues.profilePatch(EventCodec.JSON.readTree(body))).hasMessage("VALIDATION_ERROR");
  assertThat(PartyValues.profilePatch(EventCodec.JSON.readTree("{\"displayName\":\"  Synthetic  \",\"phone\":null,\"locale\":\"en-ng\"}")).path("locale").asString()).isEqualTo("en-NG");
  for(String country:new String[]{"ng","ZZ","NGA",""})assertThatThrownBy(()->PartyValues.country(country)).hasMessage("VALIDATION_ERROR");
  assertThatThrownBy(()->PartyValues.text("a\nb",32,false)).hasMessage("VALIDATION_ERROR");
 }
 @Test void addressPatchPreservesOwnedValuesAndRejectsCoercion(){
  var prior=new PartyValues.Address(java.util.UUID.randomUUID(),null,"recipient","phone","line1",null,"city",null,null,"NG",null,true,7);
  var next=PartyValues.address(EventCodec.JSON.readTree("{\"line2\":\"second line\"}"),prior);
  assertThat(next.id()).isEqualTo(prior.id());assertThat(next.version()).isEqualTo(7);assertThat(next.recipient()).isEqualTo(prior.recipient());assertThat(next.isDefault()).isTrue();
  for(String body:new String[]{"{\"partyId\":\"x\"}","{\"phone\":123}","{\"isDefault\":\"true\"}","{\"recipient\":null}"})assertThatThrownBy(()->PartyValues.address(EventCodec.JSON.readTree(body),prior)).hasMessage("VALIDATION_ERROR");
 }
 @Test void rulesAreClosedUniqueAndVersioned(){
  for(String body:new String[]{"{}","{\"schemaVersion\":1,\"requiredFields\":[],\"postalPattern\":null,\"regionCodes\":[],\"extra\":true}","{\"schemaVersion\":1,\"requiredFields\":[\"REGION\",\"REGION\"],\"postalPattern\":null,\"regionCodes\":[]}","{\"schemaVersion\":1,\"requiredFields\":[],\"postalPattern\":null,\"regionCodes\":[\"A\",\"A\"]}"})assertThatThrownBy(()->AddressRules.parse(EventCodec.JSON.readTree(body))).hasMessage("VALIDATION_ERROR");
 }
}
