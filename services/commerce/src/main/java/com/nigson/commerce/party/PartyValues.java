package com.nigson.commerce.party;

import java.util.*;
import java.text.Normalizer;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.ApiValues;

/** Closed public value types; identity and ownership are never mutable request properties. */
public final class PartyValues {
    private PartyValues() {}
    public record Profile(UUID id,String displayName,String email,boolean emailVerified,String phone,String locale,long version) {}
    public record Address(UUID id,String label,String recipient,String phone,String line1,String line2,String city,String region,String postalCode,String countryCode,UUID organizationId,boolean isDefault,long version) {}
    public record Organization(UUID id,String name,String membershipRole,String entitlementState) {}
    public record VerifiedIdentity(String issuer,String subject,String email,boolean emailVerified,String displayName) {
        public VerifiedIdentity {
            text(issuer,500,false); text(subject,200,false); text(email,254,false); text(displayName,160,false);
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw ApiValues.invalid();
            var uri=java.net.URI.create(issuer);if(!uri.isAbsolute()||uri.getHost()==null)throw ApiValues.invalid();
        }
        @Override public String toString(){return "VerifiedIdentity[protected]";}
    }
    public static String text(String value,int max,boolean nullable) {
        if(value==null){if(nullable)return null;throw ApiValues.invalid();}
        if(value.isBlank()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw ApiValues.invalid();
        String normalized=Normalizer.normalize(value.strip(),Normalizer.Form.NFC);if(normalized.length()>max)throw ApiValues.invalid();return normalized;
    }
    public static String locale(String value) {
        text(value,16,false);
        try {String canonical=new Locale.Builder().setLanguageTag(value).build().toLanguageTag();if(canonical.length()>16)throw ApiValues.invalid();return canonical;}
        catch(IllformedLocaleException bad){throw ApiValues.invalid();}
    }
    public static String country(String value){if(value==null||!Set.of(Locale.getISOCountries()).contains(value))throw ApiValues.invalid();return value;}
    public static JsonNode object(JsonNode input,Set<String> fields,boolean emptyAllowed) {
        if(input==null||!input.isObject()||!emptyAllowed&&input.isEmpty()||input.size()>fields.size())throw ApiValues.invalid();
        for(var field:input.properties())if(!fields.contains(field.getKey()))throw ApiValues.invalid();return input;
    }
    private static String value(JsonNode node,String key,int max,boolean nullable) {
        var value=node.get(key);if(value==null||value.isNull()){if(nullable)return null;throw ApiValues.invalid();}
        if(!value.isString())throw ApiValues.invalid();return text(value.asString(),max,nullable);
    }
    public static final Set<String> ADDRESS_FIELDS=Set.of("label","recipient","phone","line1","line2","city","region","postalCode","countryCode","organizationId","isDefault");
    public static Address address(JsonNode input,Address previous) {
        object(input,ADDRESS_FIELDS,false);var merged=input.deepCopy();
        if(previous!=null){var original=com.nigson.shared.events.EventCodec.JSON.valueToTree(previous);for(String key:ADDRESS_FIELDS)if(!merged.has(key))((tools.jackson.databind.node.ObjectNode)merged).set(key,original.get(key));}
        UUID organization=null;var org=merged.get("organizationId");if(org!=null&&!org.isNull()){if(!org.isString())throw ApiValues.invalid();organization=ApiValues.uuid(org.asString());}
        boolean isDefault=false;var d=merged.get("isDefault");if(d!=null){if(!d.isBoolean())throw ApiValues.invalid();isDefault=d.asBoolean();}
        return new Address(previous==null?null:previous.id(),value(merged,"label",60,true),value(merged,"recipient",160,false),value(merged,"phone",32,false),value(merged,"line1",200,false),value(merged,"line2",200,true),value(merged,"city",100,false),value(merged,"region",100,true),value(merged,"postalCode",24,true),country(value(merged,"countryCode",2,false)),organization,isDefault,previous==null?0:previous.version());
    }
    public static JsonNode profilePatch(JsonNode input) {
        object(input,Set.of("displayName","phone","locale"),false);
        var result=com.nigson.shared.events.EventCodec.JSON.createObjectNode();
        if(input.has("displayName"))result.put("displayName",value(input,"displayName",160,false));
        if(input.has("phone")){var phone=value(input,"phone",32,true);if(phone==null)result.putNull("phone");else result.put("phone",phone);}
        if(input.has("locale"))result.put("locale",locale(value(input,"locale",16,false)));return result;
    }
}
