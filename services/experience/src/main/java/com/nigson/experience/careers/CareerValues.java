package com.nigson.experience.careers;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.ApiValues;

/** Closed career API value validation; applicant answers are validated against the captured form revision. */
public final class CareerValues {
 public static final Set<String> KINDS=Set.of("JOB","INTERNSHIP","INFLUENCER","AFFILIATE");
 public static final Set<String> STATES=Set.of("SUBMITTED","REVIEWED","SHORTLISTED","REJECTED","CLOSED");
 public static final Set<String> PUBLIC_SORTS=Set.of("createdAt,asc","createdAt,desc","title,asc","title,desc");
 public static final Set<String> APPLICATION_SORTS=Set.of("createdAt,asc","createdAt,desc","name,asc","name,desc");
 private static final Pattern KEY=Pattern.compile("[a-z][a-zA-Z0-9_]{0,79}");
 public record OpportunityInput(String title,String slug,String kind,String description,JsonNode fieldsSchema,Instant closesAt,String state) {}
 public record Submission(String name,String email,JsonNode answers,UUID cvFileId,String privacyPolicyVersion,String antiSpamToken) {}
 public record Transition(String state,String reason) {}
 private CareerValues(){}
 public static void validateOpportunity(OpportunityInput v){
  if(v==null||text(v.title(),180)==null||!v.title().strip().equals(v.title())||text(v.slug(),200)==null||!v.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*")||!KINDS.contains(v.kind())||text(v.description(),20000)==null||!Set.of("DRAFT","PUBLISHED","CLOSED").contains(v.state()))throw ApiValues.invalid();validateSchema(v.fieldsSchema());
 }
 public static int validateSchema(JsonNode schema){
  if(schema==null||!schema.isObject()||!schema.has("schemaVersion")||!schema.path("schemaVersion").canConvertToInt()||schema.path("schemaVersion").asInt()<1||!schema.path("fields").isArray()||schema.path("fields").size()>30||schema.size()!=2)throw ApiValues.invalid();
  Set<String> keys=new HashSet<>();for(JsonNode f:schema.path("fields")){if(!f.isObject()||!f.has("key")||!f.has("type")||!f.has("required")||!f.has("maximumLength")||!f.path("required").isBoolean()||!f.path("maximumLength").canConvertToInt()||f.size()!=("SELECT".equals(f.path("type").asString())?5:4))throw ApiValues.invalid();String key=f.path("key").asString();String type=f.path("type").asString();if(!KEY.matcher(key).matches()||Set.of("name","email","cv","resume").contains(key.toLowerCase(Locale.ROOT))||!keys.add(key)||!Set.of("TEXT","LONG_TEXT","SELECT","BOOLEAN").contains(type)||f.path("maximumLength").asInt()<1||f.path("maximumLength").asInt()>5000)throw ApiValues.invalid();if("SELECT".equals(type)){JsonNode options=f.path("options");if(!options.isArray()||options.isEmpty()||options.size()>100)throw ApiValues.invalid();Set<String> seen=new HashSet<>();for(JsonNode o:options)if(!o.isString()||text(o.asString(),100)==null||!seen.add(o.asString()))throw ApiValues.invalid();}else if(f.has("options"))throw ApiValues.invalid();}
  return schema.path("schemaVersion").asInt();
 }
 public static void validateAnswers(JsonNode schema,JsonNode answers){validateSchema(schema);if(answers==null||!answers.isObject())throw ApiValues.invalid();Set<String> known=new HashSet<>();for(JsonNode f:schema.path("fields")){String key=f.path("key").asString();known.add(key);JsonNode value=answers.get(key);if(value==null||value.isNull()){if(f.path("required").asBoolean())throw ApiValues.invalid();continue;}String type=f.path("type").asString();if("BOOLEAN".equals(type)){if(!value.isBoolean())throw ApiValues.invalid();}else if(!value.isString()||value.asString().length()>f.path("maximumLength").asInt()||("SELECT".equals(type)&&java.util.stream.StreamSupport.stream(f.path("options").spliterator(),false).noneMatch(o->o.asString().equals(value.asString()))))throw ApiValues.invalid();}if(!known.containsAll(answers.propertyNames()))throw ApiValues.invalid();}
 public static String text(String s,int max){return s==null||s.isBlank()||s.length()>max||s.chars().anyMatch(c->c<32||c==127)?null:s;}
}
