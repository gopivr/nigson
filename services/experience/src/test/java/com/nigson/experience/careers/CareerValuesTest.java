package com.nigson.experience.careers;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.events.EventCodec;
import static org.assertj.core.api.Assertions.*;

class CareerValuesTest {
 private JsonNode schema(String value){return EventCodec.JSON.readTree(value);}
 @Test void formRevisionAcceptsClosedValidTypesAndAnswers(){var form=schema("""
  {"schemaVersion":2,"fields":[{"key":"portfolio","type":"TEXT","required":true,"maximumLength":40},{"key":"discipline","type":"SELECT","required":true,"maximumLength":20,"options":["design","engineering"]},{"key":"relocate","type":"BOOLEAN","required":false,"maximumLength":1}]}
  """);assertThat(CareerValues.validateSchema(form)).isEqualTo(2);CareerValues.validateAnswers(form,schema("{\"portfolio\":\"https://example.invalid/me\",\"discipline\":\"design\",\"relocate\":true}"));}
 @Test void schemaAndAnswersRejectReservedDuplicateUnknownWrongTypeAndUnlistedChoice(){
  assertThatThrownBy(()->CareerValues.validateSchema(schema("{\"schemaVersion\":1,\"fields\":[{\"key\":\"email\",\"type\":\"TEXT\",\"required\":true,\"maximumLength\":80}]}"))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->CareerValues.validateSchema(schema("{\"schemaVersion\":1,\"fields\":[{\"key\":\"name\",\"type\":\"TEXT\",\"required\":true,\"maximumLength\":80},{\"key\":\"name\",\"type\":\"TEXT\",\"required\":false,\"maximumLength\":80}]}"))).isInstanceOf(RuntimeException.class);
  var form=schema("{\"schemaVersion\":1,\"fields\":[{\"key\":\"degree\",\"type\":\"SELECT\",\"required\":true,\"maximumLength\":20,\"options\":[\"BSc\"]}]}" );
  assertThatThrownBy(()->CareerValues.validateAnswers(form,schema("{\"degree\":\"MSc\"}"))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->CareerValues.validateAnswers(form,schema("{\"degree\":\"BSc\",\"email\":\"applicant@example.invalid\"}"))).isInstanceOf(RuntimeException.class);
 }
 @Test void malformedOpportunityNeverReachesPersistence(){var bad=new CareerValues.OpportunityInput("Role","role","UNKNOWN","details",schema("{\"schemaVersion\":1,\"fields\":[]}"),null,"PUBLISHED");assertThatThrownBy(()->CareerValues.validateOpportunity(bad)).isInstanceOf(RuntimeException.class);}
}
