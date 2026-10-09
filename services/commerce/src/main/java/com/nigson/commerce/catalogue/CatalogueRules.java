package com.nigson.commerce.catalogue;

import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;

/** Reviewed metadata is supplied by the owner; no production unit or division is inferred. */
public final class CatalogueRules {
    public enum Type { TEXT, DECIMAL, BOOLEAN, TEXT_ARRAY, DECIMAL_ARRAY, BOOLEAN_ARRAY }
    public record Attribute(Type type, String unit, int maximumLength) {
        public Attribute {
            Objects.requireNonNull(type);
            if(maximumLength<1||maximumLength>1000||unit!=null&&!unit.matches("[A-Za-z][A-Za-z0-9_-]{0,19}"))throw new IllegalArgumentException("INVALID_CATALOGUE_METADATA");
            if(unit!=null&&type!=Type.DECIMAL&&type!=Type.DECIMAL_ARRAY)throw new IllegalArgumentException("INVALID_ATTRIBUTE_UNIT");
        }
    }
    public record Unit(String code, Set<Integer> scales) {
        public Unit {
            if(code==null||!code.matches("[A-Za-z][A-Za-z0-9_-]{0,19}")||scales==null||scales.isEmpty()||scales.stream().anyMatch(n->n==null||n<0||n>3))throw new IllegalArgumentException("INVALID_CATALOGUE_UNIT");
            scales=Set.copyOf(scales);
        }
    }
    public record Packaging(int schemaVersion,String maximumUnitsPerPackage,String unitWeightKg,String tareWeightKg,String lengthCm,String widthCm,String heightCm){}
    private final Map<String,Unit> units;
    private final Map<String,Map<String,Attribute>> divisions;
    public CatalogueRules(Collection<Unit> units,Map<String,Map<String,Attribute>> divisions) {
        var registered=new HashMap<String,Unit>();
        for(var unit:units)if(registered.put(unit.code(),unit)!=null)throw new IllegalArgumentException("DUPLICATE_UNIT");
        this.units=Map.copyOf(registered);
        var schemas=new HashMap<String,Map<String,Attribute>>();
        divisions.forEach((division,attributes)->{
            if(division==null||!division.matches("[A-Za-z][A-Za-z0-9_-]{0,39}")||attributes.size()>50)throw new IllegalArgumentException("INVALID_DIVISION");
            attributes.forEach((key,attribute)->{
                if(!key.matches("[a-z][a-z0-9_]{0,79}")||attribute==null||attribute.unit()!=null&&!registered.containsKey(attribute.unit()))throw new IllegalArgumentException("INVALID_ATTRIBUTE_METADATA");
            });
            schemas.put(division,Map.copyOf(attributes));
        });
        this.divisions=Map.copyOf(schemas);
    }
    public void requireUnit(String code,int scale) {
        if(code==null||scale<0||scale>3)throw ApiValues.invalid();
        var unit=units.get(code);
        if(unit==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        if(!unit.scales().contains(scale))throw ApiValues.invalid();
    }
    public void specifications(String division,JsonNode values) {
        CatalogueValues.specifications(values);
        if(division==null&&values.isEmpty())return;
        var schema=division==null?null:divisions.get(division);
        if(schema==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        for(var entry:values.properties()) {
            var attribute=schema.get(entry.getKey());
            if(attribute==null)throw ApiValues.invalid();
            var value=entry.getValue();
            switch(attribute.type()) {
                case TEXT -> scalar(value,Type.TEXT,attribute.maximumLength());
                case DECIMAL -> scalar(value,Type.DECIMAL,attribute.maximumLength());
                case BOOLEAN -> scalar(value,Type.BOOLEAN,attribute.maximumLength());
                case TEXT_ARRAY,DECIMAL_ARRAY,BOOLEAN_ARRAY -> {
                    if(!value.isArray()||value.size()>20)throw ApiValues.invalid();
                    Type element=switch(attribute.type()){case TEXT_ARRAY->Type.TEXT;case DECIMAL_ARRAY->Type.DECIMAL;default->Type.BOOLEAN;};
                    for(var item:value)scalar(item,element,attribute.maximumLength());
                }
            }
        }
    }
    private static void scalar(JsonNode value,Type type,int length) {
        if(type==Type.BOOLEAN){if(!value.isBoolean())throw ApiValues.invalid();return;}
        if(!value.isString()||value.asString().length()>length)throw ApiValues.invalid();
        if(type==Type.DECIMAL&&!value.asString().matches("-?[0-9]{1,17}(\\.[0-9]{1,3})?"))throw ApiValues.invalid();
    }
    public static Packaging packaging(JsonNode value,int skuScale) {
        var keys=Set.of("schemaVersion","maximumUnitsPerPackage","unitWeightKg","tareWeightKg","lengthCm","widthCm","heightCm");
        CatalogueValues.object(value,keys,false);
        if(value.size()!=keys.size()||CatalogueValues.integer(value,"schemaVersion",0)!=1||skuScale<0||skuScale>3)throw ApiValues.invalid();
        String maximum=CatalogueValues.quantity(value,"maximumUnitsPerPackage",skuScale);
        String weight=decimal(value,"unitWeightKg",true,9),tare=decimal(value,"tareWeightKg",false,9);
        String length=decimal(value,"lengthCm",true,9),width=decimal(value,"widthCm",true,9),height=decimal(value,"heightCm",true,9);
        // Numeric(12,3) shipping weights must remain representable for a full carton.
        var total=new BigDecimal(maximum).multiply(new BigDecimal(weight)).add(new BigDecimal(tare));
        if(total.compareTo(new BigDecimal("999999999.999"))>0)throw ApiValues.invalid();
        return new Packaging(1,maximum,weight,tare,length,width,height);
    }
    static String decimal(JsonNode node,String key,boolean positive,int integerDigits) {
        String input=CatalogueValues.string(node,key,30,false,false);
        if(!input.matches("[0-9]{1,"+integerDigits+"}(\\.[0-9]{1,3})?"))throw ApiValues.invalid();
        var number=new BigDecimal(input);
        if(positive&&number.signum()==0)throw ApiValues.invalid();
        return number.stripTrailingZeros().toPlainString();
    }
}
