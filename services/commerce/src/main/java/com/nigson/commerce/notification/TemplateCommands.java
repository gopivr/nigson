package com.nigson.commerce.notification;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import tools.jackson.databind.JsonNode;

/** Commerce intent/acknowledgement only. Never queries integration tables or holds locks across HTTP. */
public final class TemplateCommands {
    private static final String OP="API-165-PRIVATE-ACK";
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Clock clock;private final AuditWriter audit;
    public TemplateCommands(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock,AuditWriter audit){this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setTimeout(3);this.clock=clock;this.audit=audit;}
    public record Intent(UUID command,long generation,TemplatePort.Accepted completed,ProblemCode rejected){}
    public void authority(ActorAssertion.Actor actor,boolean mutation){
        if(actor==null||actor.organization()!=null)throw ActorAssertion.denied();if(mutation)ActorAssertion.recentMfa(actor,clock);
        var rows=jdbc.query("SELECT p.id FROM commerce.party p WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND EXISTS(SELECT 1 FROM commerce.staff_role_assignment r WHERE r.party_id=p.id AND r.role='ADMIN' AND r.revoked_at IS NULL)",(r,n)->r.getObject(1,UUID.class),actor.party(),actor.identityIssuer(),actor.identitySubject());if(rows.size()!=1)throw ActorAssertion.denied();
    }
    private void lockAuthority(ActorAssertion.Actor actor){jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());authority(actor,true);}
    private java.sql.Timestamp stamp(Instant value){return java.sql.Timestamp.from(value);}
    public Intent claim(ActorAssertion.Actor actor,TemplateValues.Template template,Long expected,String key,UUID correlation){
        authority(actor,true);ApiValues.idempotencyKey(key);UUID job=resource(template);var request=EventCodec.JSON.createObjectNode();if(expected==null)request.putNull("version");else request.put("version",expected);request.set("body",TemplateValues.wire(template));String hash=EventCodec.hash(request),principal=actor.party().toString();UUID candidate=EventCodec.uuid7(clock.instant());
        try{return tx.execute(status->{
            jdbc.execute("SET LOCAL lock_timeout='350ms'");jdbc.execute("SET LOCAL statement_timeout='800ms'");lockAuthority(actor);
            jdbc.update("INSERT INTO commerce.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,resource_id,lease_until,expires_at,response_body) VALUES (?,?,?,?,?,?,?,'RUNNING',?,NULL,?,?::jsonb) ON CONFLICT(principal_key,operation,request_key) DO NOTHING",candidate,principal,principal,principal,OP,key,hash,job,stamp(clock.instant().plusSeconds(86400)),"{\"outcome\":\"PREPARED\"}");
            var row=jdbc.queryForMap("SELECT * FROM commerce.idempotency_record WHERE principal_key=? AND operation=? AND request_key=? FOR UPDATE",principal,OP,key);
            if(!hash.equals(row.get("request_hash")))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);
            UUID id=(UUID)row.get("id");long generation=((Number)row.get("version")).longValue();var saved=EventCodec.JSON.readTree(row.get("response_body").toString());
            if(row.get("state").equals("SUCCEEDED")){
                if(saved.has("error"))return new Intent(id,generation,null,ProblemCode.valueOf(saved.get("error").asString()));
                return new Intent(id,generation,EventCodec.JSON.treeToValue(saved.get("accepted"),TemplatePort.Accepted.class),null);
            }
            var lease=(java.sql.Timestamp)row.get("lease_until");if(lease!=null&&lease.toInstant().isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);
            // An expired CALLING or UNKNOWN intent is acknowledged by replaying ONLY this command UUID.
            jdbc.update("UPDATE commerce.idempotency_record SET state='RUNNING',response_body=?::jsonb,lease_until=?,version=version+1,updated_at=?,updated_by=? WHERE id=?", "{\"outcome\":\"CALLING\"}",stamp(clock.instant().plusSeconds(30)),stamp(clock.instant()),principal,id);
            audit.append(principal,AuditValues.ActorKind.USER,AuditValues.Action.NOTIFICATION_TEMPLATE,AuditValues.Resource.NOTIFICATION,job,correlation,AuditValues.Reason.OPERATOR_REQUEST,null,Map.of("revision",template.revision(),"enabled",template.active()),null);
            return new Intent(id,generation+1,null,null);
        });}catch(org.springframework.dao.DataAccessException busy){for(Throwable t=busy;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException sql&&Set.of("55P03","57014").contains(sql.getSQLState()))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);throw busy;}
    }
    public void unknown(Intent intent,ActorAssertion.Actor actor,UUID job,UUID correlation){
        tx.executeWithoutResult(status->{limits();int changed=jdbc.update("UPDATE commerce.idempotency_record SET response_body=?::jsonb,lease_until=NULL,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=? AND state='RUNNING'","{\"outcome\":\"UNKNOWN\"}",stamp(clock.instant()),actor.party().toString(),intent.command(),intent.generation());
            if(changed==1)audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.NOTIFICATION_TEMPLATE,AuditValues.Resource.NOTIFICATION,job,correlation,AuditValues.Reason.RECONCILIATION,null,Map.of("state","UNKNOWN"),null);
        });
    }
    public void finish(Intent intent,ActorAssertion.Actor actor,UUID job,UUID correlation,TemplatePort.Accepted accepted,ProblemCode rejected){
        tx.executeWithoutResult(status->{limits();var body=EventCodec.JSON.createObjectNode();if(rejected!=null)body.put("error",rejected.name());else{body.set("accepted",EventCodec.JSON.valueToTree(accepted));}
            int changed=jdbc.update("UPDATE commerce.idempotency_record SET state='SUCCEEDED',response_status=?,response_body=?::jsonb,lease_until=NULL,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=? AND state='RUNNING'",rejected==null?200:rejected.status,EventCodec.JSON.writeValueAsString(body),stamp(clock.instant()),actor.party().toString(),intent.command(),intent.generation());
            if(changed!=1)throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);
            audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.NOTIFICATION_TEMPLATE,AuditValues.Resource.NOTIFICATION,job,correlation,AuditValues.Reason.RECONCILIATION,null,rejected==null?Map.of("revision",accepted.revision(),"enabled",accepted.active()):Map.of("redacted",true),null);
        });
    }
    public static UUID resource(TemplateValues.Template template){return UUID.nameUUIDFromBytes((template.code()+"|"+template.locale()+"|"+template.channel()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private void limits(){jdbc.execute("SET LOCAL lock_timeout='350ms'");jdbc.execute("SET LOCAL statement_timeout='800ms'");}
}
