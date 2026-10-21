package com.nigson.experience.lead;

import java.util.*;
import java.util.regex.Pattern;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import tools.jackson.databind.JsonNode;

/** Closed DTOs and bounds for the Experience-owned BP1 lead APIs. */
public final class LeadValues {
    private LeadValues() {}
    public static final Set<String> TYPES=Set.of("CONTACT","COMPLAINT","QUOTE","DISTRIBUTOR");
    public static final Set<String> STATES=Set.of("NEW","ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED");
    public static final Set<String> PRIORITIES=Set.of("NORMAL","HIGH");
    public static final Pattern EMAIL=Pattern.compile("(?i)^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$");
    public record Enquiry(String type,String name,String email,String phone,String subject,String message,String antiSpamToken) {}
    public record Created(UUID id,String state,java.time.Instant receivedAt) {}
    public record Lead(UUID id,String type,String subject,String name,String email,String phone,String message,String state,
                       String priority,UUID assignedTo,java.time.Instant followUpAt,long version,java.time.Instant createdAt) {}
    public record Activity(String kind,String note,java.time.Instant createdAt) {}
    public record Detail(UUID id,String type,String subject,String name,String email,String phone,String message,String state,String priority,
                         UUID assignedTo,java.time.Instant followUpAt,long version,java.time.Instant createdAt,List<Activity> activities) {
        public Detail { activities=List.copyOf(activities); }
        public Detail(Lead lead,List<Activity> activities){this(lead.id(),lead.type(),lead.subject(),lead.name(),lead.email(),lead.phone(),lead.message(),lead.state(),lead.priority(),lead.assignedTo(),lead.followUpAt(),lead.version(),lead.createdAt(),activities);}
    }
    public record Page<T>(List<T> items,int page,int size,long totalElements,long totalPages) { public Page { items=List.copyOf(items); } }
    public record Patch(String state,boolean hasState,UUID assignedTo,boolean hasAssignedTo,String priority,boolean hasPriority,
                        java.time.Instant followUpAt,boolean hasFollowUpAt,String note) {}
    public record Changed(Lead lead) {}
    public record NotificationContext(UUID leadId,String type,String subject,String recipientGroup,String adminLink) {}

    public static Enquiry enquiry(JsonNode body) {
        fields(body,Set.of("type","name","email","subject","message","antiSpamToken"),Set.of("phone"));
        String phone=null;if(body.has("phone")&&!body.get("phone").isNull())phone=text(body,"phone",32,false);
        return new Enquiry(text(body,"type",20,false),text(body,"name",160,false),text(body,"email",254,false),phone,
            text(body,"subject",200,false),text(body,"message",10000,false),text(body,"antiSpamToken",4096,false));
    }
    public static Patch patch(JsonNode body) {
        fields(body,Set.of("note"),Set.of("state","assignedTo","priority","followUpAt"));
        boolean state=body.has("state"),assigned=body.has("assignedTo"),priority=body.has("priority"),follow=body.has("followUpAt");
        String s=state?text(body,"state",20,false):null,p=priority?text(body,"priority",10,false):null;
        UUID assignee=null;if(assigned&&!body.get("assignedTo").isNull())assignee=uuid(body.get("assignedTo"));
        java.time.Instant followAt=null;if(follow&&!body.get("followUpAt").isNull())followAt=ApiValues.instant(text(body,"followUpAt",40,false));
        String note=text(body,"note",2000,false);
        if(!state&&!assigned&&!priority&&!follow)throw ApiValues.invalid();
        if(state&&!STATES.contains(s)||priority&&!PRIORITIES.contains(p))throw ApiValues.invalid();
        return new Patch(s,state,assignee,assigned,p,priority,followAt,follow,note);
    }
    public static String text(JsonNode body,String key,int max,boolean nullable) {
        JsonNode n=body.get(key);if(n==null||n.isNull()&&nullable)return null;
        if(!n.isString())throw ApiValues.invalid();String v=n.asString();
        if(v.isBlank()||v.length()>max||!v.equals(v.strip())||v.chars().anyMatch(c->c<32&&c!='\n'&&c!='\t'||c==127))throw ApiValues.invalid();return v;
    }
    public static UUID uuid(JsonNode n) {if(n==null||!n.isString())throw ApiValues.invalid();return ApiValues.uuid(n.asString());}
    public static void fields(JsonNode n,Set<String> required,Set<String> optional){var allowed=new HashSet<>(required);allowed.addAll(optional);if(n==null||!n.isObject()||!n.propertyNames().containsAll(required)||!allowed.containsAll(n.propertyNames()))throw ApiValues.invalid();}
    public static void validate(Enquiry x) {
        if(!Set.of("CONTACT","COMPLAINT").contains(x.type())||!EMAIL.matcher(x.email()).matches()||x.phone()!=null&&!x.phone().matches("\\+[1-9][0-9]{7,14}")||x.message().isBlank()||x.antiSpamToken().isBlank())throw ApiValues.invalid();
    }
    public static Problem.Failure denied(){return new Problem.Failure(ProblemCode.ACCESS_DENIED);}
}
