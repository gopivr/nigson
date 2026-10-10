package com.nigson.commerce.notification;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;

/** Plain templates with flat, explicitly allowlisted data variables. No expression engine. */
public final class TemplateValues {
    private TemplateValues(){}
    private static final Pattern VARIABLE=Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]{0,39})\\}\\}");
    private static final Set<String> CODES=Set.of("CONTACT_VERIFICATION","ORDER_PENDING","ORDER_PAID","ORDER_SHIPPED","ORDER_DELIVERED","ORDER_CANCELLED","REFUND_STATUS","LEAD_CREATED","DISTRIBUTOR_APPLICATION","DISTRIBUTOR_DECISION","QUOTE_OFFERED","NEWSLETTER_CONFIRMATION","CAREER_APPLICATION","STAFF_LEAD_REPLY","CART_RECOVERY");
    private static final Set<String> VARIABLES=Set.of("code","expiresAt","orderNumber","total","currency","trackingUrl","refundStatus","amount","leadNumber","displayName","applicationNumber","opportunityTitle","confirmationUrl","quoteNumber","status","message");
    public record Template(String code,int revision,String locale,String channel,String classification,String subject,String body,String providerTemplateId,JsonNode variablesSchema,boolean active,String reason){@Override public String toString(){return "Template[protected]";}}
    public record View(String code,long revision){}
    public static JsonNode wire(Template value){var n=EventCodec.JSON.createObjectNode().put("code",value.code()).put("revision",value.revision()).put("locale",value.locale()).put("channel",value.channel()).put("classification",value.classification()).put("body",value.body()).put("active",value.active());n.set("variablesSchema",value.variablesSchema().deepCopy());if(value.subject()!=null)n.put("subject",value.subject());if(value.providerTemplateId()!=null)n.put("providerTemplateId",value.providerTemplateId());if(value.reason()!=null)n.put("reason",value.reason());return n;}
    public static Template template(JsonNode input,int phase){
        if(input==null||!input.isObject()||EventCodec.JSON.writeValueAsBytes(input).length>32768||!Set.of("code","revision","locale","channel","classification","subject","body","providerTemplateId","variablesSchema","active","reason").containsAll(input.propertyNames()))throw ApiValues.invalid();
        String code=text(input,"code",80,false);if(!CODES.contains(code))throw rule();
        var revision=input.get("revision");if(revision==null||!revision.isIntegralNumber()||!revision.canConvertToInt()||revision.asInt()<1)throw ApiValues.invalid();
        String locale=text(input,"locale",16,false);try{var parsed=new Locale.Builder().setLanguageTag(locale).build();if(parsed.getLanguage().isBlank())throw ApiValues.invalid();locale=parsed.toLanguageTag();}catch(IllformedLocaleException invalid){throw ApiValues.invalid();}
        String channel=text(input,"channel",20,false),classification=text(input,"classification",20,false);if(!Set.of("EMAIL","SMS","WHATSAPP").contains(channel)||!Set.of("TRANSACTIONAL","MARKETING").contains(classification))throw ApiValues.invalid();
        var enabled=input.get("active");if(enabled==null||!enabled.isBoolean())throw ApiValues.invalid();boolean active=enabled.asBoolean();int required=Set.of("NEWSLETTER_CONFIRMATION","CAREER_APPLICATION").contains(code)?2:Set.of("DISTRIBUTOR_APPLICATION","DISTRIBUTOR_DECISION","QUOTE_OFFERED").contains(code)?3:code.equals("CART_RECOVERY")?4:1;if(phase<1||phase>5||active&&phase<required)throw rule();
        if(Set.of("NEWSLETTER_CONFIRMATION","CART_RECOVERY").contains(code)&&!classification.equals("MARKETING"))throw rule();
        String subject=input.has("subject")?text(input,"subject",200,false):null,body=text(input,"body",16384,true),provider=input.has("providerTemplateId")?text(input,"providerTemplateId",160,false):null,reason=input.has("reason")?text(input,"reason",500,false):null;
        if(provider!=null&&!provider.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,159}")||active&&channel.equals("WHATSAPP")&&provider==null||!channel.equals("EMAIL")&&subject!=null)throw rule();
        JsonNode schema=input.get("variablesSchema");var properties=variables(schema);var used=new HashSet<String>();used.addAll(used(body));if(subject!=null)used.addAll(used(subject));if(!properties.equals(used))throw rule();
        return new Template(code,revision.asInt(),locale,channel,classification,subject,body,provider,schema.deepCopy(),active,reason);
    }
    private static String text(JsonNode input,String name,int max,boolean multiline){var value=input.get(name);if(value==null||!value.isString())throw ApiValues.invalid();String text=value.asString();if(text.isBlank()||text.length()>max||text.codePoints().anyMatch(c->Character.getType(c)==Character.FORMAT||Character.isISOControl(c)&&!(multiline&&(c=='\n'||c=='\t'))))throw ApiValues.invalid();return text;}
    private static Set<String> variables(JsonNode schema){
        if(schema==null||!schema.isObject()||schema.size()!=4||!Set.of("type","properties","additionalProperties","required").containsAll(schema.propertyNames())||!schema.path("type").isString()||!schema.path("type").asString().equals("object")||!schema.path("additionalProperties").isBoolean()||schema.path("additionalProperties").asBoolean()||!schema.path("properties").isObject()||schema.path("properties").size()>20||!schema.path("required").isArray())throw rule();
        Set<String> names=new HashSet<>(schema.get("properties").propertyNames());if(!VARIABLES.containsAll(names))throw rule();
        for(var property:schema.get("properties").properties()){var value=property.getValue();if(!value.isObject()||value.size()!=2||!Set.of("type","maxLength").containsAll(value.propertyNames())||!value.path("type").isString()||!value.path("type").asString().equals("string")||!value.path("maxLength").isIntegralNumber()||!value.path("maxLength").canConvertToInt()||value.path("maxLength").asInt()<1||value.path("maxLength").asInt()>2000)throw rule();}
        var required=new HashSet<String>();for(var value:schema.get("required")){if(!value.isString()||!required.add(value.asString()))throw rule();}if(!required.equals(names))throw rule();return names;
    }
    private static Set<String> used(String template){if(template.contains("{{{")||template.contains("}}}"))throw rule();var matcher=VARIABLE.matcher(template);var names=new HashSet<String>();while(matcher.find())names.add(matcher.group(1));String literal=matcher.replaceAll("");if(literal.contains("{{")||literal.contains("}}"))throw rule();return names;}
    public static String render(Template template,Map<String,String> input){
        var names=variables(template.variablesSchema());if(!input.keySet().equals(names))throw rule();for(var name:names){String value=input.get(name);if(value==null||value.length()>template.variablesSchema().get("properties").get(name).get("maxLength").asInt()||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw rule();}
        var matcher=VARIABLE.matcher(template.body());var output=new StringBuilder();int from=0;while(matcher.find()){output.append(escape(template.body().substring(from,matcher.start()),template.channel())).append(escape(input.get(matcher.group(1)),template.channel()));from=matcher.end();}output.append(escape(template.body().substring(from),template.channel()));if(output.length()>65536)throw rule();return output.toString();
    }
    private static String escape(String value,String channel){return channel.equals("EMAIL")?value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"):value;}
    private static Problem.Failure rule(){return new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);}
}
