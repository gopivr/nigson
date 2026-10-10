package com.nigson.commerce.catalogue;

import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.nigson.commerce.platform.ConfigurationPolicy;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;

/** Defines metadata shape only. No real catalogue units/divisions are seeded. */
@Component
public class CatalogueMetadataPolicy implements ConfigurationPolicy {
    public String key(){return "catalogue.metadata";}
    private static void exact(JsonNode node,Set<String> fields){CatalogueValues.object(node,fields,false);if(node.size()!=fields.size())throw ApiValues.invalid();}
    public static CatalogueRules parse(JsonNode node) {
        exact(node,Set.of("schemaVersion","units","divisions"));
        if(CatalogueValues.integer(node,"schemaVersion",0)!=1||!node.get("units").isArray()||!node.get("divisions").isArray()||node.get("units").size()>100||node.get("divisions").size()>100)throw ApiValues.invalid();
        var units=new ArrayList<CatalogueRules.Unit>();var divisions=new HashMap<String,Map<String,CatalogueRules.Attribute>>();
        try {
            for(var unit:node.get("units")){
                exact(unit,Set.of("code","scales"));var scales=new HashSet<Integer>();
                if(!unit.get("scales").isArray()||unit.get("scales").isEmpty()||unit.get("scales").size()>4)throw ApiValues.invalid();
                for(var scale:unit.get("scales")){if(!scale.isIntegralNumber()||!scale.canConvertToInt()||!scales.add(scale.asInt()))throw ApiValues.invalid();}
                units.add(new CatalogueRules.Unit(CatalogueValues.string(unit,"code",20,false,false),scales));
            }
            for(var division:node.get("divisions")){
                exact(division,Set.of("code","attributes"));var attributes=new HashMap<String,CatalogueRules.Attribute>();
                if(!division.get("attributes").isArray()||division.get("attributes").size()>50)throw ApiValues.invalid();
                for(var attribute:division.get("attributes")){
                    exact(attribute,Set.of("key","type","unit","maximumLength"));
                    String key=CatalogueValues.string(attribute,"key",80,false,false);
                    var definition=new CatalogueRules.Attribute(CatalogueRules.Type.valueOf(CatalogueValues.string(attribute,"type",20,false,false)),CatalogueValues.string(attribute,"unit",20,true,false),CatalogueValues.integer(attribute,"maximumLength",0));
                    if(attributes.put(key,definition)!=null)throw ApiValues.invalid();
                }
                if(divisions.put(CatalogueValues.string(division,"code",40,false,false),attributes)!=null)throw ApiValues.invalid();
            }
            return new CatalogueRules(units,divisions);
        } catch(IllegalArgumentException invalid){throw ApiValues.invalid();}
    }
    public void validate(JsonNode value){parse(value);}
    public void activate(JdbcTemplate jdbc,JsonNode value){
        var rules=parse(value);
        if(value.get("units").isEmpty())throw new Problem.Failure(ProblemCode.PLATFORM_RULE_VIOLATION);
        // Existing records retain their immutable unit/scale and specification meaning.
        jdbc.query("SELECT unit,quantity_scale FROM commerce.sku",(org.springframework.jdbc.core.RowCallbackHandler)row->rules.requireUnit(row.getString(1),row.getInt(2)));
        jdbc.query("SELECT division_code,specifications::text FROM commerce.product",(org.springframework.jdbc.core.RowCallbackHandler)row->rules.specifications(row.getString(1),EventCodec.JSON.readTree(row.getString(2))));
    }
}
