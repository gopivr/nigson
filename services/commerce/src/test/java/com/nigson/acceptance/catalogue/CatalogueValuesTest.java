package com.nigson.acceptance.catalogue;

import com.nigson.commerce.catalogue.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CatalogueValuesTest {
    private JsonNode json(String input){return EventCodec.JSON.readTree(input);}
    private CatalogueRules rules(){return new CatalogueRules(List.of(new CatalogueRules.Unit("synthetic_unit",Set.of(0,3))),Map.of("synthetic",Map.of("weight",new CatalogueRules.Attribute(CatalogueRules.Type.DECIMAL,"synthetic_unit",30),"certified",new CatalogueRules.Attribute(CatalogueRules.Type.BOOLEAN,null,1),"labels",new CatalogueRules.Attribute(CatalogueRules.Type.TEXT_ARRAY,null,8))));}
    @Test void registeredTypesAndMissingMetadataFailClosed(){
        rules().specifications("synthetic",json("{\"weight\":\"12.125\",\"certified\":true,\"labels\":[\"short\"]}"));
        for(String invalid:List.of("{\"weight\":12}","{\"weight\":\"1e9\"}","{\"certified\":\"true\"}","{\"labels\":[true]}","{\"labels\":[\"too lengthy\"]}","{\"unregistered\":\"x\"}","{\"labels\":[\"<script>\"]}"))assertThatThrownBy(()->rules().specifications("synthetic",json(invalid))).isInstanceOf(Problem.Failure.class);
        assertThatThrownBy(()->rules().specifications(null,json("{\"weight\":\"1\"}"))).isInstanceOf(Problem.Failure.class).extracting(e->((Problem.Failure)e).code).isEqualTo(ProblemCode.FEATURE_DISABLED);
        rules().requireUnit("synthetic_unit",3);
        assertThatThrownBy(()->rules().requireUnit("synthetic_unit",2)).isInstanceOf(Problem.Failure.class);
        assertThatThrownBy(()->rules().requireUnit("unknown",0)).isInstanceOf(Problem.Failure.class);
    }
    @Test void packagingHasExactTypesScaleAndBoundedWeight(){
        String valid="{\"schemaVersion\":1,\"maximumUnitsPerPackage\":\"4\",\"unitWeightKg\":\"1.125\",\"tareWeightKg\":\"0\",\"lengthCm\":\"12\",\"widthCm\":\"9\",\"heightCm\":\"8\"}";
        var parsed=CatalogueRules.packaging(json(valid),0);assertThat(parsed.tareWeightKg()).isEqualTo("0");
        for(String invalid:List.of(valid.replace("\"schemaVersion\":1","\"schemaVersion\":1.0"),valid.replace("\"schemaVersion\":1","\"schemaVersion\":2"),valid.replace("\"4\"","\"4.5\""),valid.replace("\"1.125\"","\"0\""),valid.replace("\"1.125\"","1.125"),valid.replace("\"4\"","\"99999999999999999\""),valid.replace("\"8\"","\"-8\""),valid.replace("}",",\"arbitrary\":true}")))assertThatThrownBy(()->CatalogueRules.packaging(json(invalid),0)).isInstanceOf(Problem.Failure.class);
    }
    @Test void exactProductPatchPreservesTaxonomyAndMultilineDescription(){
        UUID id=UUID.randomUUID();String input="{\"name\":\"Synthetic\",\"slug\":\"synthetic\",\"description\":\"Line one\\nLine two\",\"specifications\":{},\"categoryIds\":[\""+id+"\"]}";
        var before=CatalogueValues.product(json(input),null);var after=CatalogueValues.product(json("{\"name\":\" Updated \"}"),before);
        assertThat(after.name()).isEqualTo("Updated");assertThat(after.description()).contains("\n");assertThat(after.categoryIds()).containsExactly(id);
        for(String invalid:List.of("{\"status\":\"ACTIVE\"}","{\"name\":null}","{}","{\"slug\":\"Upper Case\"}","{\"categoryIds\":[\""+id+"\",\""+id+"\"]}"))assertThatThrownBy(()->CatalogueValues.product(json(invalid),before)).isInstanceOf(Problem.Failure.class);
    }
}
