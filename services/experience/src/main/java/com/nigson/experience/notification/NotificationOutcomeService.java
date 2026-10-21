package com.nigson.experience.notification;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.experience.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.security.PrivateWorkload;

/** Experience-owned, append-only API-137 outcome accounting. It never dispatches provider work. */
public final class NotificationOutcomeService {
    private final JdbcTemplate jdbc;private final PlatformStore store;private final AuditWriter audit;private final MeterRegistry metrics;
    public NotificationOutcomeService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,MeterRegistry metrics){this.jdbc=Objects.requireNonNull(jdbc);this.store=Objects.requireNonNull(store);this.audit=Objects.requireNonNull(audit);this.metrics=Objects.requireNonNull(metrics);}

    public NotificationValues.Acknowledged record(PrivateWorkload.Proof proof,NotificationValues.Delivery input,String key,UUID correlation){
        authorize(proof);if(input==null||key==null||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();String idem=ApiValues.idempotencyKey(key);
        var request=EventCodec.JSON.valueToTree(Map.of("jobId",input.jobId(),"resourceId",input.resourceId(),"state",input.state(),"providerReference",Objects.toString(input.providerReference(),"")));
        var saved=store.executeLocal("service:nigson-integration","API-137",idem,request,db->{
            db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"notification-delivery:"+input.jobId());
            if(!ownsResource(db,input.resourceId()))throw new Problem.Failure(ProblemCode.NOT_FOUND);
            var history=db.query("SELECT new_value->>'state' AS state,new_value->>'resourceId' AS resource_id FROM experience.audit_record WHERE resource_type='NOTIFICATION' AND action='NOTIFICATION_DELIVERY' AND resource_id=? ORDER BY occurred_at,id",(r,n)->Map.of("state",Objects.toString(r.getString("state"),""),"resourceId",Objects.toString(r.getString("resource_id"),"")),input.jobId());
            if(!history.isEmpty()){
                String bound=(String)history.getFirst().get("resourceId");if(bound==null||!bound.equals(input.resourceId().toString()))throw new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);
                String latest=(String)history.getLast().get("state");
                if(input.state().equals(latest))return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(new NotificationValues.Acknowledged(true)),input.jobId());
                if(!"UNKNOWN".equals(latest)||!Set.of("SENT","FAILED").contains(input.state()))throw NotificationValues.transitionDenied();
            }
            audit.append("nigson-integration",AuditValues.ActorKind.SERVICE,AuditValues.Action.NOTIFICATION_DELIVERY,AuditValues.Resource.NOTIFICATION,input.jobId(),correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("state",input.state(),"resourceId",input.resourceId().toString()),null);
            return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(new NotificationValues.Acknowledged(true)),input.jobId());
        });
        if(saved.status()==200)metrics.counter("nigson.notification.delivery","owner","experience","state",input.state()).increment();
        return EventCodec.JSON.treeToValue(saved.body(),NotificationValues.Acknowledged.class);
    }
    private static void authorize(PrivateWorkload.Proof proof){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-137")&&r.owner().equals("experience")&&r.client().equals("nigson-integration")&&r.scope().equals("notification.write")).findFirst().orElseThrow(ActorAssertion::denied);if(proof==null||!route.equals(proof.route())||proof.actor()!=null||proof.subject()==null||proof.subject().isBlank()||!route.client().equals(proof.client()))throw ActorAssertion.denied();}
    private static boolean ownsResource(JdbcTemplate db,UUID id){return Boolean.TRUE.equals(db.queryForObject("SELECT (EXISTS(SELECT 1 FROM experience.lead WHERE id=?) OR EXISTS(SELECT 1 FROM experience.newsletter_subscription WHERE id=?) OR EXISTS(SELECT 1 FROM experience.career_application WHERE id=?) OR EXISTS(SELECT 1 FROM experience.distributor_application WHERE id=?) OR EXISTS(SELECT 1 FROM experience.destination_verification WHERE id=?) OR EXISTS(SELECT 1 FROM experience.communication_destination WHERE id=?) OR EXISTS(SELECT 1 FROM experience.inbound_message WHERE id=?) OR EXISTS(SELECT 1 FROM experience.review WHERE id=?) OR EXISTS(SELECT 1 FROM experience.campaign WHERE id=?))",Boolean.class,id,id,id,id,id,id,id,id,id));}
}
