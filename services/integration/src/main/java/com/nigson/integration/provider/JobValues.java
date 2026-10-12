package com.nigson.integration.provider;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;

/** Private job views deliberately omit provider inputs and execution keys. */
public final class JobValues {
 private JobValues(){}
 public enum Kind { SHEETS_SYNC,LOGISTICS_CREATE,PAYMENT_RECONCILE,ASSET_SCAN,REPORT_EXPORT }
 public enum State { PENDING,CLAIMED,SUCCEEDED,FAILED,UNKNOWN }
 public record Job(UUID id,String state,int attemptCount,Instant nextAttemptAt,String lastErrorCode){
  public Job{if(id==null||id.equals(new UUID(0,0))||state==null||attemptCount<0||nextAttemptAt==null)throw ApiValues.invalid();try{State.valueOf(state);if(lastErrorCode!=null)ProblemCode.valueOf(lastErrorCode);}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}}
 }
 public record Page(List<Job> items,int page,int size,long totalElements,long totalPages){public Page{items=List.copyOf(items);}}
 public record Accepted(Job job,long version){}
 public record Query(Kind kind,State state,int page,int size,String sort){
  public Query{ApiValues.page(page,size);ApiValues.sort(sort,Set.of("nextAttemptAt,asc","nextAttemptAt,desc","id,asc","id,desc"));}
 }
 public record Retry(String reason){public Retry{validateReason(reason);}@Override public String toString(){return "Retry[protected]";}}
 private static void validateReason(String text){if(text==null||text.isBlank()||text.length()>500||text.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();}
 public static Retry retry(JsonNode input){
  if(input==null||!input.isObject()||input.size()!=1||!input.has("reason")||!input.path("reason").isString())throw ApiValues.invalid();
  String text=input.path("reason").asString();if(text.isBlank()||text.length()>500||text.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return new Retry(text);
 }
 public static Kind kind(String input){try{return input==null?null:Kind.valueOf(input);}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}}
 public static State state(String input){try{return input==null?null:State.valueOf(input);}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}}
 public static int number(String input,int fallback){if(input==null)return fallback;try{if(!input.matches("0|[1-9][0-9]{0,9}"))throw ApiValues.invalid();return Integer.parseInt(input);}catch(NumberFormatException invalid){throw ApiValues.invalid();}}
}
