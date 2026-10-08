package com.nigson.shared.problem;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.lang.reflect.*;
/** Values only; endpoint schemas must supply their declared allowlists. */
public final class ApiValues {
 private ApiValues(){}
 public static Problem.Failure invalid(){return new Problem.Failure(ProblemCode.VALIDATION_ERROR);}
 public static UUID uuid(String input){try {if(input==null||!input.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))throw invalid();UUID id=UUID.fromString(input);if(id.equals(new UUID(0,0)))throw invalid();return id;}catch(IllegalArgumentException bad){throw invalid();}}
 public static BigDecimal money(String input,boolean nonnegative){return decimal(input,15,4,nonnegative);}
 public static BigDecimal quantity(String input){BigDecimal value=decimal(input,17,3,true);if(value.signum()==0)throw invalid();return value;}
 private static BigDecimal decimal(String value,int precision,int scale,boolean nonnegative){if(value==null||!value.matches((nonnegative?"":"-?")+"[0-9]{1,"+precision+"}(\\.[0-9]{1,"+scale+"})?"))throw invalid();return new BigDecimal(value);}
 public static String idempotencyKey(String value){if(value==null||value.isBlank()||value.length()>100||!value.matches("[\\x20-\\x7e]+"))throw invalid();return value;}
 public static long version(String value){try {if(value==null||!value.matches("\"(0|[1-9][0-9]{0,18})\""))throw invalid();return Long.parseLong(value.substring(1,value.length()-1));}catch(NumberFormatException bad){throw invalid();}}
 public static Instant instant(String value){try {if(value==null||value.length()>40)throw invalid();return OffsetDateTime.parse(value).toInstant();}catch(java.time.format.DateTimeParseException bad){throw invalid();}}
 public static void page(int page,int size){if(page<0||size<1||size>100)throw invalid();}
 public static void reportWindow(Instant start,Instant end){if(start==null||end==null||start.isAfter(end)||Duration.between(start,end).compareTo(Duration.ofDays(366))>0)throw invalid();}
 public static void query(Map<String,String> values,Set<String> allowed){if(values.size()>20||!allowed.containsAll(values.keySet())||values.values().stream().anyMatch(v->v==null||v.length()>200))throw invalid();}
 public static void sort(String value,Set<String> allowed){if(value==null||!allowed.contains(value))throw invalid();}
 /** Structural JSON caps, applied before DTO conversion. Endpoint-specific lower caps remain mandatory. */
 public static void jsonShape(byte[] bytes,int arrayLimit){
  if(arrayLimit<1||arrayLimit>500)throw invalid();
  try {var mapper=tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();var root=mapper.readTree(bytes);if(root==null)throw invalid();record Item(tools.jackson.databind.JsonNode node,int depth){}var pending=new ArrayDeque<Item>();pending.push(new Item(root,0));
   while(!pending.isEmpty()){var item=pending.pop();var node=item.node();if(item.depth()>32||(node.isArray()&&node.size()>arrayLimit)||(node.isObject()&&node.size()>100))throw invalid();if(node.isContainer())for(var child:node)pending.push(new Item(child,item.depth()+1));}
  }catch(tools.jackson.core.JacksonException bad){throw invalid();}
 }
 /** Derive a field path from the trusted DTO type; map keys and unknown input names become $. */
 public static String field(Class<?> type,String path){
  if(path==null||path.length()>100||!path.matches("[A-Za-z][A-Za-z0-9]*(\\[[0-9]{1,2}\\])?(\\.[A-Za-z][A-Za-z0-9]*(\\[[0-9]{1,2}\\])?)*"))return "$";
  try {for(String part:path.split("\\.")){String name=part.replaceAll("\\[[0-9]+\\]","");Field f=type.getDeclaredField(name);Type generic=f.getGenericType();type=f.getType();if(part.contains("[")){if(!(generic instanceof ParameterizedType p)||!(p.getActualTypeArguments()[0] instanceof Class<?> item))return "$";type=item;}}return path;}catch(ReflectiveOperationException bad){return "$";}
 }
}
