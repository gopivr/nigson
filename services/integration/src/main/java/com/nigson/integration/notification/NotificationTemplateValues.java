package com.nigson.integration.notification;

import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import java.util.*;
import java.util.regex.*;
import tools.jackson.databind.JsonNode;

/** Closed API-142 DTO and compile-time validation for data-only notification templates. */
public final class NotificationTemplateValues {
    private static final Pattern NAME=Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");
    private static final Pattern TOKEN=Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]{0,39})\\}\\}");
    private NotificationTemplateValues(){}

    public record Command(String code,int revision,String locale,String channel,String classification,
                          String subject,String body,String providerTemplateId,JsonNode variablesSchema,
                          boolean active,String reason) {
        public Command { variablesSchema=variablesSchema.deepCopy(); }
        @Override public JsonNode variablesSchema(){return variablesSchema.deepCopy();}
        @Override public String toString(){return "NotificationTemplateCommand[protected]";}
    }
    public record View(UUID id,String code,int revision,boolean active) {}

    public static Command parse(JsonNode n){
        Set<String> required=Set.of("code","revision","locale","channel","classification","body","variablesSchema","active");
        if(n==null||!n.isObject()||!n.propertyNames().containsAll(required)||!Set.of("code","revision","locale","channel","classification","subject","body","providerTemplateId","variablesSchema","active","reason").containsAll(n.propertyNames())||EventCodec.JSON.writeValueAsBytes(n).length>12000)
            throw ApiValues.invalid();
        try{
            String code=text(n,"code",80),locale=locale(text(n,"locale",16)),channel=text(n,"channel",20),classification=text(n,"classification",20),body=text(n,"body",10000);
            if(!n.path("revision").isIntegralNumber()||!n.path("revision").canConvertToInt()||!n.path("active").isBoolean())throw invalid();int revision=n.path("revision").asInt(-1);if(!code.matches("[A-Z][A-Z0-9_]{0,79}")||revision<1||revision>1_000_000||!Set.of("EMAIL","SMS","WHATSAPP").contains(channel)||!Set.of("TRANSACTIONAL","MARKETING").contains(classification))throw invalid();
            String subject=optional(n,"subject",200),provider=optional(n,"providerTemplateId",160),reason=optional(n,"reason",500);
            if(reason!=null&&reason.length()<10)throw invalid();if(subject!=null&&subject.isBlank()||body.isBlank())throw invalid();if(provider!=null&&!provider.matches("[A-Za-z0-9._:-]{1,160}"))throw invalid();
            JsonNode schema=n.path("variablesSchema");validateSchema(schema,body);
            return new Command(code,revision,locale,channel,classification,subject,body,provider,schema,n.path("active").asBoolean(),reason);
        }catch(Problem.Failure failure){throw failure;}catch(RuntimeException malformed){throw invalid();}
    }
    private static String text(JsonNode n,String key,int max){JsonNode v=n.path(key);if(!v.isString()||v.asString().isBlank()||v.asString().length()>max||v.asString().codePoints().anyMatch(Character::isISOControl))throw invalid();return v.asString();}
    private static String optional(JsonNode n,String key,int max){JsonNode v=n.path(key);if(v.isMissingNode()||v.isNull())return null;if(!v.isString()||v.asString().length()>max||v.asString().codePoints().anyMatch(Character::isISOControl))throw invalid();return v.asString();}
    private static String locale(String raw){try{var value=new Locale.Builder().setLanguageTag(raw).build();if(value.getLanguage().isBlank()||!value.toLanguageTag().equalsIgnoreCase(raw))throw new IllegalArgumentException();return value.toLanguageTag();}catch(RuntimeException bad){throw invalid();}}
    private static void validateSchema(JsonNode schema,String body){
        if(!schema.isObject()||!schema.propertyNames().equals(Set.of("type","additionalProperties","properties","required"))||!"object".equals(schema.path("type").asString())||!schema.path("additionalProperties").isBoolean()||schema.path("additionalProperties").asBoolean()||!schema.path("properties").isObject()||schema.path("properties").size()>20||!schema.path("required").isArray()||schema.path("required").size()>20)throw invalid();
        var props=schema.path("properties");var required=new HashSet<String>();for(JsonNode field:schema.path("required")){if(!field.isString()||!NAME.matcher(field.asString()).matches()||!required.add(field.asString()))throw invalid();}if(!props.propertyNames().containsAll(required))throw invalid();
        for(var field:props.properties()){if(!NAME.matcher(field.getKey()).matches()||!field.getValue().isObject()||!field.getValue().propertyNames().equals(Set.of("type","maxLength"))||!"string".equals(field.getValue().path("type").asString())||!field.getValue().path("maxLength").isIntegralNumber()||field.getValue().path("maxLength").asInt()<1||field.getValue().path("maxLength").asInt()>1000)throw invalid();}
        if(body.contains("{{{")||body.contains("}}}")||body.contains("{%")||body.contains("${"))throw invalid();Matcher m=TOKEN.matcher(body);var used=new HashSet<String>();while(m.find()){String name=m.group(1);if(!props.propertyNames().contains(name))throw invalid();used.add(name);}if(body.replaceAll("\\{\\{[A-Za-z][A-Za-z0-9_]{0,39}}}","").contains("{{")||body.replaceAll("\\{\\{[A-Za-z][A-Za-z0-9_]{0,39}}}","").contains("}}")||!used.containsAll(required))throw invalid();
    }
    public static String render(String body,Map<String,String> values,boolean html){
        if(body==null||values==null)throw invalid();Matcher m=TOKEN.matcher(body);StringBuffer out=new StringBuffer();while(m.find()){String value=values.get(m.group(1));if(value==null)throw invalid();m.appendReplacement(out,Matcher.quoteReplacement(html?escape(value):value));}m.appendTail(out);if(out.length()>20000)throw invalid();return out.toString();
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static Problem.Failure invalid(){return new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);}
}
