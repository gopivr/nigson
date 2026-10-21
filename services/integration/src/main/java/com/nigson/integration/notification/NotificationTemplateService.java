package com.nigson.integration.notification;

import com.nigson.integration.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.security.PrivateWorkload;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;

/** API-142 owner-local immutable notification template revisions. */
public final class NotificationTemplateService {
    public record Created(PlatformStore.Response response,long version){}
    private final PlatformStore store;private final AuditWriter audit;private final MeterRegistry metrics;private final JdbcTemplate jdbc;private final Clock clock;
    public NotificationTemplateService(PlatformStore store,AuditWriter audit,MeterRegistry metrics,JdbcTemplate jdbc,Clock clock){this.store=Objects.requireNonNull(store);this.audit=Objects.requireNonNull(audit);this.metrics=Objects.requireNonNull(metrics);this.jdbc=Objects.requireNonNull(jdbc);this.clock=Objects.requireNonNull(clock);}

    public Created create(PrivateWorkload.Proof proof,NotificationTemplateValues.Command c,Long expected,String key,UUID correlation){
        authorize(proof);if(c==null||expected!=null&&(expected<0||expected==Long.MAX_VALUE)||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        var actor=proof.actor();String principal=actor.identityIssuer()+"|"+actor.identitySubject()+"|"+actor.party();
        var request=EventCodec.JSON.createObjectNode();request.set("template",EventCodec.JSON.valueToTree(c));if(expected==null)request.putNull("expectedVersion");else request.put("expectedVersion",expected.longValue());
        var result=store.executeLocal(principal,"API-142",key,request,jdbc->{
            jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
            var rows=jdbc.queryForList("SELECT id,revision,version FROM integration.notification_template WHERE code=? AND locale=? AND channel=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",c.code(),c.locale(),c.channel());
            long actual=rows.isEmpty()?0:((Number)rows.getFirst().get("version")).longValue();int lastRevision=rows.isEmpty()?0:((Number)rows.getFirst().get("revision")).intValue();
            if(expected!=null&&actual!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);if(c.revision()!=lastRevision+1)throw new PlatformStore.Conflict("TEMPLATE_REVISION_CONFLICT");
            UUID id=EventCodec.uuid7(clock.instant());long version=actual+1;
            jdbc.update("INSERT INTO integration.notification_template(id,created_by,updated_by,version,code,revision,locale,channel,subject,body,classification,active,provider_template_id,variables_schema) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",id,actor.party().toString(),actor.party().toString(),version,c.code(),c.revision(),c.locale(),c.channel(),c.subject(),c.body(),c.classification(),c.active(),c.providerTemplateId(),EventCodec.JSON.writeValueAsString(c.variablesSchema()));
            audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.NOTIFICATION_TEMPLATE,AuditValues.Resource.NOTIFICATION,id,correlation,AuditValues.Reason.CONFIGURATION_CHANGE,null,Map.of("revision",c.revision(),"channel",c.channel(),"state",c.active()?"ACTIVE":"PENDING"),null);
            var response=EventCodec.JSON.valueToTree(new NotificationTemplateValues.View(id,c.code(),c.revision(),c.active()));return new PlatformStore.Response(201,response,id);
        });
        metrics.counter("nigson.notification.templates","result","accepted").increment();long version=jdbc.queryForObject("SELECT version FROM integration.notification_template WHERE id=?",Long.class,result.resource());return new Created(result,version);
    }
    private void authorize(PrivateWorkload.Proof p){var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-142")&&r.owner().equals("integration")&&r.client().equals("nigson-commerce")).findFirst().orElseThrow(ActorAssertion::denied);if(p==null||!route.equals(p.route())||!route.client().equals(p.client())||p.actor()==null||!p.actor().roles().contains("ADMIN")||!p.actor().scopes().contains(route.scope())||p.actor().organization()!=null)throw ActorAssertion.denied();ActorAssertion.recentMfa(p.actor(),clock);}
}
