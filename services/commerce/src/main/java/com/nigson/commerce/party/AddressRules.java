package com.nigson.commerce.party;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
/** Versioned owner configuration; RE2/J avoids catastrophic regex backtracking. */
public record AddressRules(Set<String> requiredFields,com.google.re2j.Pattern postalPattern,Set<String> regionCodes) {
    public AddressRules {requiredFields=Set.copyOf(requiredFields);regionCodes=Set.copyOf(regionCodes);}
    public static AddressRules parse(JsonNode input){
        PartyValues.object(input,Set.of("schemaVersion","requiredFields","postalPattern","regionCodes"),false);
        if(input.size()!=4||!input.path("schemaVersion").isIntegralNumber()||!input.path("schemaVersion").canConvertToInt()||input.path("schemaVersion").asInt()!=1||!input.path("requiredFields").isArray()||input.path("requiredFields").size()>6||!input.path("regionCodes").isArray()||input.path("regionCodes").size()>100)throw ApiValues.invalid();
        var fields=new HashSet<String>();for(var f:input.get("requiredFields")){if(!f.isString()||!Set.of("RECIPIENT","PHONE","LINE1","CITY","REGION","POSTAL_CODE").contains(f.asString())||!fields.add(f.asString()))throw ApiValues.invalid();}
        var regions=new HashSet<String>();for(var r:input.get("regionCodes")){if(!r.isString()||!regions.add(PartyValues.text(r.asString(),100,false)))throw ApiValues.invalid();}
        var pattern=input.get("postalPattern");com.google.re2j.Pattern compiled=null;
        if(!pattern.isNull()){if(!pattern.isString()||pattern.asString().isBlank()||pattern.asString().length()>120)throw ApiValues.invalid();try{compiled=com.google.re2j.Pattern.compile(pattern.asString());if(compiled.programSize()>10000)throw ApiValues.invalid();}catch(com.google.re2j.PatternSyntaxException bad){throw ApiValues.invalid();}}
        return new AddressRules(fields,compiled,regions);
    }
    public void validate(PartyValues.Address address){
        if(requiredFields.contains("REGION")&&address.region()==null||requiredFields.contains("POSTAL_CODE")&&address.postalCode()==null||!regionCodes.isEmpty()&&(address.region()==null||!regionCodes.contains(address.region()))||postalPattern!=null&&address.postalCode()!=null&&!postalPattern.matcher(address.postalCode()).matches())throw new Problem.Failure(ProblemCode.PARTY_RULE_VIOLATION);
    }
}
