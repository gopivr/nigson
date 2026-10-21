package com.nigson.experience.lead;

import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.experience.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import tools.jackson.databind.JsonNode;

/** Experience-local durable public intake, staff workflow and Integration context. */
public final class LeadService {
    @FunctionalInterface public interface AntiSpamVerifier { boolean verify(String token,String idempotencyKey); }
    @FunctionalInterface public interface RateLimiter { boolean allow(String requestSource); }
    private static final Pattern RECIPIENT=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");
    private final JdbcTemplate jdbc;private final PlatformStore store;private final AuditWriter audit;private final MeterRegistry metrics;
    private final Clock clock;private final TransactionTemplate reads;private final AntiSpamVerifier antiSpam;private final RateLimiter rateLimiter;
    private final String recipientGroup,adminBaseUrl;
    public LeadService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager manager,
                       Clock clock,AntiSpamVerifier antiSpam,RateLimiter rateLimiter,String recipientGroup,String adminBaseUrl){
        this.jdbc=Objects.requireNonNull(jdbc);this.store=Objects.requireNonNull(store);this.audit=Objects.requireNonNull(audit);this.metrics=Objects.requireNonNull(metrics);this.clock=Objects.requireNonNull(clock);
        this.antiSpam=antiSpam;this.rateLimiter=rateLimiter;this.recipientGroup=recipientGroup;this.adminBaseUrl=validBase(adminBaseUrl);
        reads=new TransactionTemplate(manager);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(3);
    }
    private static String validBase(String value){if(value==null||value.isBlank())return null;try{URI u=URI.create(value);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!Set.of("","/").contains(u.getPath())||value.length()>512)throw new IllegalArgumentException();return u.toASCIIString().replaceAll("/$","");}catch(RuntimeException e){throw new IllegalArgumentException("LEAD_ADMIN_URL_INVALID");}}
    private boolean recipientReady(){return recipientGroup!=null&&RECIPIENT.matcher(recipientGroup).matches();}
    private static UUID correlation(UUID value){return value==null?WireContext.correlation(null):value;}
    private ActorAssertion.Actor staff(PrivateWorkload.Proof p,String api){
        var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals(api)&&r.owner().equals("experience")&&r.client().equals("nigson-commerce")).findFirst().orElseThrow(ActorAssertion::denied);
        if(p==null||p.actor()==null||!route.equals(p.route())||!"nigson-commerce".equals(p.client())||p.actor().organization()!=null||!(p.actor().roles().contains("SALES")||p.actor().roles().contains("ADMIN"))||!p.actor().scopes().contains(route.scope()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return p.actor();
    }
    private static void integration(PrivateWorkload.Proof p){
        var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-129")&&r.owner().equals("experience")&&r.client().equals("nigson-integration")).findFirst().orElseThrow(ActorAssertion::denied);
        if(p==null||p.actor()!=null||!route.equals(p.route())||!"nigson-integration".equals(p.client()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
    private UUID correlation(){var wire=WireContext.current();return wire==null?WireContext.correlation(null):wire.correlationId;}

    public LeadValues.Created create(LeadValues.Enquiry input,String key,String requestSource,UUID corr){
        if(!recipientReady()||antiSpam==null||rateLimiter==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        key=ApiValues.idempotencyKey(key);LeadValues.validate(input);if(corr==null||corr.equals(new UUID(0,0)))throw ApiValues.invalid();
        JsonNode request=EventCodec.JSON.valueToTree(input);var replay=store.replayLocal("public:lead","API-087",key,request);
        if(replay.isPresent())return EventCodec.JSON.treeToValue(replay.get().body(),LeadValues.Created.class);
        if(requestSource==null||requestSource.length()>256||!rateLimiter.allow(requestSource))throw new Problem.Failure(ProblemCode.RATE_LIMITED);
        boolean verified;try{verified=antiSpam.verify(input.antiSpamToken(),key);}catch(RuntimeException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
        if(!verified)throw new Problem.Failure(ProblemCode.BUSINESS_RULE_VIOLATION);
        UUID id=EventCodec.uuid7(clock.instant());String email=input.email().strip().toLowerCase(Locale.ROOT);String phone=input.phone()==null?null:input.phone().strip();
        var saved=store.executeLocal("public:lead","API-087",key,request,db->{Instant now=clock.instant();
            db.update("INSERT INTO experience.lead(id,created_by,updated_by,type,name,email,phone,subject,message,status,priority,source) VALUES (?,?,?,?,?,?,?,?,?,'NEW','NORMAL','WEB_ENQUIRY')",
                id,"public:lead","public:lead",input.type(),input.name().strip(),email,phone,input.subject().strip(),input.message());
            db.update("INSERT INTO experience.lead_activity(id,created_by,lead_id,activity_type,note,from_status,to_status,actor_party_id) VALUES (?,?,?,'RECEIVED','Public enquiry received',NULL,'NEW',NULL)",EventCodec.uuid7(now),"public:lead",id);
            appendCreated(id,input.type(),"NORMAL",corr,now);audit.append("public:lead",AuditValues.ActorKind.SYSTEM,AuditValues.Action.LEAD_CREATE,AuditValues.Resource.LEAD,id,corr,AuditValues.Reason.OPERATOR_REQUEST,null,Map.of("type",input.type(),"state","NEW"),null);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){metrics.counter("nigson.leads.created","owner","experience","type",input.type()).increment();}});
            return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(new LeadValues.Created(id,"NEW",now)),id);
        });return EventCodec.JSON.treeToValue(saved.body(),LeadValues.Created.class);
    }
    private void appendCreated(UUID id,String type,String priority,UUID corr,Instant now){
        var event=EventCodec.JSON.createObjectNode();event.put("event_id",EventCodec.uuid7(now).toString());event.put("event_type","lead.created.v1");event.put("schema_version",1);event.put("producer","experience");event.put("occurred_at",now.toString());event.put("aggregate_id",id.toString());event.put("aggregate_version",0);event.putNull("organization_id");event.put("correlation_id",corr.toString());
        var span=io.opentelemetry.api.trace.Span.current().getSpanContext();if(span.isValid())event.put("traceparent","00-"+span.getTraceId()+"-"+span.getSpanId()+"-"+span.getTraceFlags().asHex());else event.putNull("traceparent");
        event.set("payload",EventCodec.JSON.valueToTree(Map.of("lead_id",id,"type",type,"priority",priority,"recipient_group",recipientGroup)));store.append(EventCodec.JSON.writeValueAsString(event),"public:lead");
    }
    public LeadValues.Page<LeadValues.Lead> list(PrivateWorkload.Proof proof,String type,String state,String priority,UUID assignedTo,int page,int size,String sort){
        var actor=staff(proof,"API-089");bounds(page,size);if(type!=null&&!LeadValues.TYPES.contains(type)||state!=null&&!LeadValues.STATES.contains(state)||priority!=null&&!LeadValues.PRIORITIES.contains(priority)||!Set.of("createdAt,desc","createdAt,asc","priority,desc","priority,asc","followUpAt,asc","followUpAt,desc").contains(sort))throw ApiValues.invalid();
        boolean admin=actor.roles().contains("ADMIN");if(!admin&&assignedTo!=null&&!assignedTo.equals(actor.party()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        String order=switch(sort){case "createdAt,asc"->"created_at ASC,id ASC";case "priority,desc"->"priority DESC,created_at DESC,id ASC";case "priority,asc"->"priority ASC,created_at DESC,id ASC";case "followUpAt,desc"->"follow_up_at DESC NULLS LAST,id ASC";case "followUpAt,asc"->"follow_up_at ASC NULLS LAST,id ASC";default->"created_at DESC,id ASC";};
        return reads.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2500ms'");String visibility=admin?"TRUE":"(assigned_to=? OR assigned_to IS NULL)";String filter=" WHERE (?::varchar IS NULL OR type=?) AND (?::varchar IS NULL OR status=?) AND (?::varchar IS NULL OR priority=?) AND (?::uuid IS NULL OR assigned_to=?) AND "+visibility;
            List<Object> args=new ArrayList<>();Collections.addAll(args,type,type,state,state,priority,priority,assignedTo,assignedTo);if(!admin)args.add(actor.party());
            List<Object> queryArgs=new ArrayList<>(args);queryArgs.add(size);queryArgs.add(Math.multiplyExact(page,size));
            var rows=jdbc.query("SELECT id,type,subject,name,email,phone,message,status,priority,assigned_to,follow_up_at,version,created_at FROM experience.lead"+filter+" ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->readLead(r),queryArgs.toArray());
            Long total=jdbc.queryForObject("SELECT count(*) FROM experience.lead"+filter,Long.class,args.toArray());for(var lead:rows)auditRead(actor,lead.id(),correlation());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){metrics.counter("nigson.leads.queries","owner","experience","operation","list").increment();}});
            return new LeadValues.Page<>(rows,page,size,total==null?0:total,(total==null||total==0)?0:(total+size-1)/size);
        });
    }
    public LeadValues.Detail detail(PrivateWorkload.Proof proof,UUID id){var actor=staff(proof,"API-090");if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();
        return reads.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2500ms'");var rows=jdbc.query("SELECT id,type,subject,name,email,phone,message,status,priority,assigned_to,follow_up_at,version,created_at FROM experience.lead WHERE id=?",(r,n)->readLead(r),id);if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);var lead=rows.getFirst();if(!actor.roles().contains("ADMIN")&&!Objects.equals(lead.assignedTo(),actor.party())&&lead.assignedTo()!=null)throw new Problem.Failure(ProblemCode.NOT_FOUND);
            var activities=jdbc.query("SELECT activity_type,note,created_at FROM experience.lead_activity WHERE lead_id=? ORDER BY created_at,id LIMIT 200",(r,n)->new LeadValues.Activity(r.getString(1),r.getString(2),r.getTimestamp(3).toInstant()),id);auditRead(actor,id,correlation());TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){metrics.counter("nigson.leads.queries","owner","experience","operation","detail").increment();}});return new LeadValues.Detail(lead,activities);});
    }
    public LeadValues.Lead update(PrivateWorkload.Proof proof,UUID id,LeadValues.Patch patch,long expected,String key,UUID corr){
        var actor=staff(proof,"API-091");if(id==null||id.equals(new UUID(0,0))||expected<0||patch==null||corr==null||corr.equals(new UUID(0,0)))throw ApiValues.invalid();ApiValues.idempotencyKey(key);
        if(patch.hasAssignedTo()&&patch.assignedTo()!=null&&!patch.assignedTo().equals(actor.party()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        var request=EventCodec.JSON.valueToTree(Map.of("id",id,"patch",patch,"version",expected));var result=store.executeLocal(actor.party().toString(),"API-091:"+id,key,request,db->{var rows=db.queryForList("SELECT id,type,subject,name,email,phone,message,status,priority,assigned_to,follow_up_at,version,created_at FROM experience.lead WHERE id=? FOR UPDATE",id);if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);LeadValues.Lead before=readLead(rows.getFirst());if(!actor.roles().contains("ADMIN")&&!Objects.equals(before.assignedTo(),actor.party())&&before.assignedTo()!=null)throw new Problem.Failure(ProblemCode.NOT_FOUND);if(before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            String nextState=patch.hasState()?patch.state():before.state();if(!allowed(before.state(),nextState))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);UUID nextAssignee=patch.hasAssignedTo()?patch.assignedTo():before.assignedTo();String nextPriority=patch.hasPriority()?patch.priority():before.priority();Instant nextFollow=patch.hasFollowUpAt()?patch.followUpAt():before.followUpAt();
            Instant now=clock.instant();int changed=db.update("UPDATE experience.lead SET status=?,assigned_to=?,priority=?,follow_up_at=?,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?",nextState,nextAssignee,nextPriority,nextFollow==null?null:Timestamp.from(nextFollow),Timestamp.from(now),actor.party().toString(),id,expected);if(changed!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            String activity=patch.hasAssignedTo()?"ASSIGNMENT":"UPDATED";db.update("INSERT INTO experience.lead_activity(id,created_by,lead_id,activity_type,note,from_status,to_status,actor_party_id) VALUES (?,?,?,?,?,?,?,?)",EventCodec.uuid7(now),actor.party().toString(),id,activity,patch.note(),before.state().equals(nextState)?null:before.state(),before.state().equals(nextState)?null:nextState,actor.party());
            audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.LEAD_UPDATE,AuditValues.Resource.LEAD,id,corr,AuditValues.Reason.OPERATOR_REQUEST,Map.of("state",before.state(),"version",expected),Map.of("state",nextState,"version",expected+1,"assigned",nextAssignee!=null,"priority",nextPriority),null);
            LeadValues.Lead after=new LeadValues.Lead(before.id(),before.type(),before.subject(),before.name(),before.email(),before.phone(),before.message(),nextState,nextPriority,nextAssignee,nextFollow,expected+1,before.createdAt());return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(after),id);
        });return EventCodec.JSON.treeToValue(result.body(),LeadValues.Lead.class);
    }
    private static boolean allowed(String from,String to){if(from.equals(to))return true;return switch(from){case "NEW"->Set.of("ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED").contains(to);case "ASSIGNED"->Set.of("IN_PROGRESS","RESOLVED","CLOSED").contains(to);case "IN_PROGRESS"->Set.of("RESOLVED","CLOSED").contains(to);case "RESOLVED"->to.equals("CLOSED");default->false;};}
    public LeadValues.NotificationContext notificationContext(PrivateWorkload.Proof proof,UUID id){integration(proof);if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();if(!recipientReady()||adminBaseUrl==null)throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);
        return reads.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2s'");var rows=jdbc.queryForList("SELECT id,type,subject FROM experience.lead WHERE id=?",id);if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);var event=jdbc.queryForList("SELECT payload #>> '{payload,recipient_group}' AS recipient_group FROM experience.outbox_event WHERE aggregate_id=? AND event_type='lead.created.v1' ORDER BY created_at,id LIMIT 1",id);if(event.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);String group=(String)event.getFirst().get("recipient_group");if(group==null||!RECIPIENT.matcher(group).matches())throw new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);
            audit.append("nigson-integration",AuditValues.ActorKind.SERVICE,AuditValues.Action.LEAD_READ,AuditValues.Resource.LEAD,id,correlation(),AuditValues.Reason.SECURITY_POLICY,null,Map.of("access","NOTIFICATION_CONTEXT"),null);return new LeadValues.NotificationContext(id,(String)rows.getFirst().get("type"),(String)rows.getFirst().get("subject"),group,adminBaseUrl+"/staff/leads/"+id);
        });
    }
    private void auditRead(ActorAssertion.Actor actor,UUID id,UUID corr){audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.LEAD_READ,AuditValues.Resource.LEAD,id,corr,AuditValues.Reason.OPERATOR_REQUEST,null,Map.of("access","READ"),null);}
    private static LeadValues.Lead readLead(java.sql.ResultSet r)throws java.sql.SQLException{return new LeadValues.Lead(r.getObject("id",UUID.class),r.getString("type"),r.getString("subject"),r.getString("name"),r.getString("email"),r.getString("phone"),r.getString("message"),r.getString("status"),r.getString("priority"),r.getObject("assigned_to",UUID.class),r.getTimestamp("follow_up_at")==null?null:r.getTimestamp("follow_up_at").toInstant(),r.getLong("version"),r.getTimestamp("created_at").toInstant());}
    private static LeadValues.Lead readLead(Map<String,Object> r){return new LeadValues.Lead((UUID)r.get("id"),(String)r.get("type"),(String)r.get("subject"),(String)r.get("name"),(String)r.get("email"),(String)r.get("phone"),(String)r.get("message"),(String)r.get("status"),(String)r.get("priority"),(UUID)r.get("assigned_to"),r.get("follow_up_at") instanceof Timestamp t?t.toInstant():null,((Number)r.get("version")).longValue(),((Timestamp)r.get("created_at")).toInstant());}
    private static void bounds(int page,int size){if(page<0||page>100000||size<1||size>100)throw ApiValues.invalid();}
}
