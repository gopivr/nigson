package com.nigson.experience.distributor;

import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class DistributorValues {
 private DistributorValues(){}
 public record Submission(String organizationName,String countryCode,String businessReference,List<Document> documents){}
 public record Document(UUID fileId,String type){}
 public record Decision(String decision,String reason,UUID marketId,UUID priceBookId){}
 public record Application(UUID id,String state,UUID organizationId,java.time.Instant submittedAt,long version){}
 public record Detail(UUID id,String organizationName,String countryCode,String businessReference,String state,List<DocumentDetail> documents,UUID reviewerPartyId,String reviewReason,UUID organizationId,long version){}
 public record DocumentDetail(UUID fileId,String type,String scanStatus){}
 public record Page(List<Application> content,int page,int size,long totalElements,int totalPages,String sort){}
 public static Submission submission(JsonNode n){exact(n,Set.of("organizationName","countryCode","documentIds"),Set.of("businessReference"));String name=text(n,"organizationName",200),country=text(n,"countryCode",2);if(!country.matches("[A-Z]{2}"))throw ApiValues.invalid();String ref=n.has("businessReference")?text(n,"businessReference",100):null;JsonNode docs=n.path("documentIds");if(!docs.isArray()||docs.isEmpty()||docs.size()>20)throw ApiValues.invalid();var list=new ArrayList<Document>();var seen=new HashSet<UUID>();for(JsonNode d:docs){if(!d.isString())throw ApiValues.invalid();UUID id=ApiValues.uuid(d.asString());if(!seen.add(id))throw ApiValues.invalid();list.add(new Document(id,""));}return new Submission(name,country,ref,List.copyOf(list));}
 public static Decision decision(JsonNode n){exact(n,Set.of("decision","reason"),Set.of("marketId","priceBookId"));String d=text(n,"decision",10),reason=text(n,"reason",500);if(!Set.of("APPROVE","REJECT","SUSPEND").contains(d)||reason.isBlank())throw ApiValues.invalid();UUID market=n.has("marketId")?ApiValues.uuid(text(n,"marketId",36)):null,book=n.has("priceBookId")?ApiValues.uuid(text(n,"priceBookId",36)):null;if((d.equals("REJECT")&&(market!=null||book!=null))||(d.equals("APPROVE")&&book!=null&&market==null))throw ApiValues.invalid();return new Decision(d,reason,market,book);}
 public static String text(JsonNode n,String field,int max){if(!n.path(field).isString())throw ApiValues.invalid();String v=n.path(field).asString();if(v.isBlank()||v.length()>max||!v.equals(v.strip())||v.codePoints().anyMatch(Character::isISOControl))throw ApiValues.invalid();return v;}
 public static void exact(JsonNode n,Set<String> required,Set<String> optional){if(n==null||!n.isObject()||!n.propertyNames().containsAll(required)||!new HashSet<>(required){{addAll(optional);}}.containsAll(n.propertyNames()))throw ApiValues.invalid();}
}
