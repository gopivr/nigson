package com.nigson.experience.notification;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import tools.jackson.databind.JsonNode;

/** Closed API-137 wire values. Provider metadata is deliberately not retained by Experience. */
public final class NotificationValues {
    private NotificationValues() {}
    public static final Set<String> STATES=Set.of("SENT","FAILED","SUPPRESSED","UNKNOWN");
    private static final Pattern REFERENCE=Pattern.compile("^[A-Za-z0-9][A-Za-z0-9:_-]{0,159}$");
    public record Delivery(UUID jobId, UUID resourceId, String state, String providerReference) {}
    public record Acknowledged(boolean acknowledged) {}

    public static Delivery delivery(JsonNode body) {
        Set<String> allowed=Set.of("jobId","resourceId","state","providerReference");
        if(body==null||!body.isObject()||!body.propertyNames().containsAll(Set.of("jobId","resourceId","state"))||!allowed.containsAll(body.propertyNames()))throw ApiValues.invalid();
        UUID job=uuid(body.get("jobId"));UUID resource=uuid(body.get("resourceId"));
        String state=text(body.get("state"),16);if(!STATES.contains(state))throw ApiValues.invalid();
        String reference=null;if(body.has("providerReference")){reference=text(body.get("providerReference"),160);if(!REFERENCE.matcher(reference).matches())throw ApiValues.invalid();}
        return new Delivery(job,resource,state,reference);
    }
    private static UUID uuid(JsonNode n){if(n==null||!n.isString())throw ApiValues.invalid();String s=n.asString();if(!s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))throw ApiValues.invalid();UUID id=UUID.fromString(s);if(id.equals(new UUID(0,0)))throw ApiValues.invalid();return id;}
    private static String text(JsonNode n,int max){if(n==null||!n.isString())throw ApiValues.invalid();String s=n.asString();if(s.isBlank()||s.length()>max||!s.equals(s.strip())||s.chars().anyMatch(c->c<32||c>126))throw ApiValues.invalid();return s;}
    public static Problem.Failure transitionDenied(){return new Problem.Failure(ProblemCode.INVALID_TRANSITION);}
}
