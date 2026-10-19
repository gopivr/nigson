package com.nigson.commerce.authorization;

import java.util.*;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import tools.jackson.databind.JsonNode;

public final class EntitlementValues {
 private EntitlementValues(){}
 public enum Decision { ACTIVATE,SUSPEND }
 public record Command(UUID commandId,UUID applicationId,UUID partyId,String organizationName,String countryCode,String businessReference,Decision decision,UUID marketId,UUID priceBookId){}
 public record Acknowledgement(UUID commandId,UUID organizationId,String state,long revision){}
 public static Command parse(JsonNode n){
  if(n==null||!n.isObject()||n.size()<7||n.size()>9||!n.has("commandId")||!n.has("applicationId")||!n.has("partyId")||!n.has("organizationName")||!n.has("countryCode")||!n.has("decision")||!n.has("marketId")||n.has("businessReference")&&!n.path("businessReference").isTextual()||n.has("priceBookId")&&!n.path("priceBookId").isTextual())throw ApiValues.invalid();
  for(var p:n.properties())if(!Set.of("commandId","applicationId","partyId","organizationName","countryCode","businessReference","decision","marketId","priceBookId").contains(p.getKey()))throw ApiValues.invalid();
  String name=text(n,"organizationName",200),country=text(n,"countryCode",2),ref=n.hasNonNull("businessReference")?text(n,"businessReference",100):null,decision=text(n,"decision",10);
  if(name.isBlank()||!country.matches("[A-Z]{2}")||ref!=null&&ref.isBlank())throw ApiValues.invalid();
  Decision parsed;try{parsed=Decision.valueOf(decision);}catch(Exception invalid){throw ApiValues.invalid();}
  UUID book=n.hasNonNull("priceBookId")?uuid(n,"priceBookId"):null;
  return new Command(uuid(n,"commandId"),uuid(n,"applicationId"),uuid(n,"partyId"),name,country,ref,parsed,uuid(n,"marketId"),book);
 }
 private static String text(JsonNode n,String k,int max){if(!n.path(k).isString()||n.path(k).asString().length()>max||n.path(k).asString().chars().anyMatch(c->c<32||c>126))throw ApiValues.invalid();return n.path(k).asString();}
 private static UUID uuid(JsonNode n,String k){try{UUID v=UUID.fromString(text(n,k,36));if(v.equals(new UUID(0,0)))throw new IllegalArgumentException();return v;}catch(Exception invalid){throw ApiValues.invalid();}}
 public static Problem.Failure transition(){return new Problem.Failure(ProblemCode.INVALID_TRANSITION);}
}
