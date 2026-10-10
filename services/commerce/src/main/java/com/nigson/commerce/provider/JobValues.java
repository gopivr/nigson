package com.nigson.commerce.provider;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;

/** Closed staff job contract. No provider payload, recipient or upstream error text. */
public final class JobValues {
    private JobValues(){}
    public static final Set<String> STATES=Set.of("PENDING","CLAIMED","SUCCEEDED","FAILED","UNKNOWN");
    public record Job(UUID id,String state,int attemptCount,Instant nextAttemptAt,String lastErrorCode){}
    public record Page(List<Job> items,int page,int size,long totalElements,long totalPages){public Page{items=List.copyOf(items);}}
    public record Query(String state,int page,int size,String sort){
        public Query {ApiValues.page(page,size);if(state!=null&&!STATES.contains(state))throw ApiValues.invalid();ApiValues.sort(sort,Set.of("nextAttemptAt,asc","nextAttemptAt,desc","id,asc","id,desc"));}
    }
    public record Retry(String reason){@Override public String toString(){return "Retry[reason=REDACTED]";}}
    private static void exact(JsonNode input,Set<String> names){if(input==null||!input.isObject()||input.size()!=names.size()||!names.containsAll(input.propertyNames()))throw ApiValues.invalid();}
    public static Retry retry(JsonNode input){
        exact(input,Set.of("reason"));var n=input.get("reason");if(!n.isString())throw ApiValues.invalid();String reason=n.asString();
        if(reason.isBlank()||reason.length()>500||reason.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return new Retry(reason);
    }
    private static long integer(JsonNode n,long max){if(n==null||!n.isIntegralNumber()||!n.canConvertToLong()||n.asLong()<0||n.asLong()>max)throw ApiValues.invalid();return n.asLong();}
    public static Job job(JsonNode n){
        exact(n,Set.of("id","state","attemptCount","nextAttemptAt","lastErrorCode"));
        if(!n.get("id").isString()||!n.get("state").isString()||!STATES.contains(n.get("state").asString())||!n.get("nextAttemptAt").isString())throw ApiValues.invalid();
        var error=n.get("lastErrorCode");if(!error.isNull()&&(!error.isString()||!error.asString().matches("[A-Z][A-Z0-9_]{0,99}")))throw ApiValues.invalid();
        return new Job(ApiValues.uuid(n.get("id").asString()),n.get("state").asString(),(int)integer(n.get("attemptCount"),Integer.MAX_VALUE),ApiValues.instant(n.get("nextAttemptAt").asString()),error.isNull()?null:error.asString());
    }
    public static Page page(JsonNode n,Query q){
        exact(n,Set.of("items","page","size","totalElements","totalPages"));if(!n.get("items").isArray()||n.get("items").size()>q.size())throw ApiValues.invalid();
        int page=(int)integer(n.get("page"),Integer.MAX_VALUE),size=(int)integer(n.get("size"),100);long total=integer(n.get("totalElements"),Long.MAX_VALUE),pages=integer(n.get("totalPages"),Long.MAX_VALUE);
        if(page!=q.page()||size!=q.size()||pages!=total/size+(total%size==0?0:1)||n.get("items").size()>total)throw ApiValues.invalid();
        var jobs=new ArrayList<Job>();var ids=new HashSet<UUID>();for(var item:n.get("items")){var job=job(item);if(!ids.add(job.id()))throw ApiValues.invalid();jobs.add(job);}return new Page(jobs,page,size,total,pages);
    }
}
