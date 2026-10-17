package com.nigson.experience.lead;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.experience.consent.*;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Fresh actor-bound private context for the Integration enqueue boundary. */
public final class LeadMessagingContext {
    public record Context(String phone,String channel,String destinationHash,boolean eligible,String basis,List<String> approvedTemplateCodes) {
        public Context { approvedTemplateCodes=List.copyOf(approvedTemplateCodes); }
        @Override public String toString(){return "LeadMessagingContext[protected]";}
    }
    private final JdbcTemplate jdbc;private final PhoneSecrets phones;private final InboundMessageSecrets content;private final AuditWriter audit;private final MeterRegistry metrics;private final Clock clock;private final TransactionTemplate tx;private final List<String> templates;
    public LeadMessagingContext(JdbcTemplate jdbc,PhoneSecrets phones,InboundMessageSecrets content,AuditWriter audit,MeterRegistry metrics,Clock clock,PlatformTransactionManager manager,List<String> templates){
        this.jdbc=Objects.requireNonNull(jdbc);this.phones=Objects.requireNonNull(phones);this.content=Objects.requireNonNull(content);this.audit=Objects.requireNonNull(audit);this.metrics=Objects.requireNonNull(metrics);this.clock=Objects.requireNonNull(clock);this.tx=new TransactionTemplate(manager);this.tx.setTimeout(3);
        if(templates==null||templates.size()>10||templates.stream().anyMatch(v->!"STAFF_LEAD_REPLY".equals(v)))throw new IllegalArgumentException("LEAD_TEMPLATE_POLICY_INVALID");this.templates=List.copyOf(templates);
    }
    public Context read(PrivateWorkload.Proof proof,UUID leadId,UUID correlation){
        if(proof==null||!proof.route().api().equals("API-194")||!proof.route().owner().equals("experience")||!"nigson-integration".equals(proof.client())||proof.actor()==null||!proof.actor().roles().contains("SALES")||!proof.actor().scopes().contains("notification.read_WITH_SIGNED_ACTOR"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        if(leadId==null||leadId.equals(new UUID(0,0))||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        return tx.execute(status->{
            jdbc.execute("SET LOCAL statement_timeout='2s'");var leads=jdbc.queryForList("SELECT party_id,source,type FROM experience.lead WHERE id=?",leadId);if(leads.size()!=1||!"CONTACT".equals(leads.getFirst().get("type")))throw new Problem.Failure(ProblemCode.NOT_FOUND);
            String phone=null,hash=null,basis=null;var inbound=jdbc.queryForList("SELECT id,sender_hash,sender_ciphertext,retention_until FROM experience.inbound_message WHERE lead_id=? AND provider='TWILIO' AND channel='WHATSAPP' AND disposition='LEAD_CREATED'",leadId);
            if(inbound.size()==1){var row=inbound.getFirst();hash=(String)row.get("sender_hash");lock(hash);if(suppressed(hash))throw rule();Timestamp until=(Timestamp)row.get("retention_until");byte[] encrypted=(byte[])row.get("sender_ciphertext");if(until!=null&&until.toInstant().isAfter(clock.instant())&&encrypted!=null){UUID id=(UUID)row.get("id");phone=PhoneValues.phone(content.decrypt(id,"SENDER",encrypted));if(!phones.fingerprint(phone).equals(hash))throw rule();basis="SIGNED_INBOUND_REQUEST";}}
            if(phone==null){Object party=leads.getFirst().get("party_id");if(party==null)throw rule();var active=jdbc.queryForList("SELECT id,destination_hash,address_ciphertext,hash_key_version,consented_purposes FROM experience.communication_destination WHERE party_id=? AND channel='WHATSAPP' AND status='ACTIVE' FOR SHARE",party);if(active.size()!=1)throw rule();var row=active.getFirst();hash=(String)row.get("destination_hash");lock(hash);if(((Number)row.get("hash_key_version")).intValue()!=phones.hashVersion()||suppressed(hash)||!purposes(row.get("consented_purposes")).contains("ORDER_UPDATES")||!granted((UUID)party,hash))throw rule();phone=PhoneValues.phone(phones.decrypt((UUID)row.get("id"),"ADDRESS",(byte[])row.get("address_ciphertext")));if(!phones.fingerprint(phone).equals(hash))throw rule();basis="VERIFIED_DESTINATION";}
            audit.append("nigson-integration",AuditValues.ActorKind.SERVICE,AuditValues.Action.NOTIFICATION_DELIVERY,AuditValues.Resource.NOTIFICATION,leadId,correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("context","SALES_LEAD_REPLY","basis",basis,"channel","WHATSAPP"),null);
            metrics.counter("nigson.messaging.context.read","owner","experience","basis",basis).increment();return new Context(phone,"WHATSAPP",hash,true,basis,templates);
        });
    }
    private void lock(String hash){jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"destination:"+hash+":WHATSAPP");}
    private boolean suppressed(String hash){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM experience.communication_suppression WHERE destination_hash=? AND channel='WHATSAPP')",Boolean.class,hash));}
    private boolean granted(UUID party,String hash){return jdbc.query("SELECT granted FROM experience.consent_event WHERE party_id=? AND destination_hash=? AND channel='WHATSAPP' AND purpose='ORDER_UPDATES' ORDER BY occurred_at DESC,created_at DESC,id DESC LIMIT 1",(r,n)->r.getBoolean(1),party,hash).stream().findFirst().orElse(false);}
    private Set<String> purposes(Object value){var json=EventCodec.JSON.readTree(value.toString());if(!json.isArray()||json.isEmpty()||json.size()>3)throw rule();var out=new HashSet<String>();for(var item:json)if(!item.isString()||!PhoneValues.PURPOSES.contains(item.asString())||!out.add(item.asString()))throw rule();return out;}
    private static Problem.Failure rule(){return new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);}
}
