package com.nigson.experience.lead;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.experience.consent.*;
import com.nigson.experience.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.CallbackSignatures;

/** Twilio signature verification precedes an atomic Experience-owned inbound transaction. */
public final class InboundMessageProcessor {
    private static final String PATH="/api/v1/webhooks/notifications/twilio/whatsapp";
    private static final Pattern SID=Pattern.compile("SM[0-9a-fA-F]{32}");
    private static final Pattern PHONE=Pattern.compile("\\+234[1-9][0-9]{9}");
    private static final Set<String> FIELDS=Set.of("AccountSid","MessageSid","From","To","Body");
    private final JdbcTemplate jdbc; private final AuditWriter audit; private final PlatformStore store; private final PhoneSecrets phones;
    private final InboundMessageSecrets secrets; private final MeterRegistry metrics; private final Clock clock; private final TransactionTemplate tx;
    private final String account, token, sender, publicUrl; private final int retentionDays;
    public InboundMessageProcessor(JdbcTemplate jdbc,AuditWriter audit,PlatformStore store,PhoneSecrets phones,InboundMessageSecrets secrets,
        MeterRegistry metrics,Clock clock,PlatformTransactionManager manager,String account,String token,String sender,String publicUrl,int retentionDays) {
        this.jdbc=jdbc;this.audit=audit;this.store=store;this.phones=phones;this.secrets=secrets;this.metrics=metrics;this.clock=clock;this.tx=new TransactionTemplate(manager);this.tx.setTimeout(5);
        if(account==null||!account.matches("AC[0-9a-fA-F]{32}")||token==null||token.length()<32||sender==null||!sender.matches("whatsapp:\\+234[1-9][0-9]{9}")||publicUrl==null||!publicUrl.startsWith("https://")||!publicUrl.endsWith(PATH)||publicUrl.contains("?")||retentionDays<1||retentionDays>3650)throw new IllegalArgumentException("INBOUND_PROVIDER_POLICY_REQUIRED");
        this.account=account;this.token=token;this.sender=sender;this.publicUrl=publicUrl;this.retentionDays=retentionDays;
    }
    public String accept(String signature,Map<String,String> form,UUID correlation) {
        if(form==null||form.size()<FIELDS.size()||form.size()>100||!form.keySet().containsAll(FIELDS)||form.entrySet().stream().anyMatch(e->e.getKey()==null||!e.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,99}")||e.getValue()==null||e.getValue().length()>4096)||!account.equals(form.get("AccountSid"))||!SID.matcher(form.get("MessageSid")).matches()||!sender.equals(form.get("To"))||form.get("Body").isBlank()||form.get("Body").length()>4096||!form.get("Body").matches("(?s)[^\\p{Cc}]*"))throw ApiValues.invalid();
        String from=form.get("From");if(from==null||!from.startsWith("whatsapp:")||!PHONE.matcher(from.substring(9)).matches())throw ApiValues.invalid();
        if(!CallbackSignatures.twilio(publicUrl,form,signature,token))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        String phone=from.substring(9), hash=phones.fingerprint(phone), body=form.get("Body");
        String disposition=command(body); UUID message=EventCodec.uuid7(clock.instant());
        String result=tx.execute(status->{
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"twilio-inbound:"+form.get("MessageSid"));
            var old=jdbc.query("SELECT id,sender_hash,sender_ciphertext,body_ciphertext,disposition FROM experience.inbound_message WHERE provider='TWILIO' AND provider_message_id=? FOR UPDATE",(r,n)->new Object[]{r.getObject("id",UUID.class),r.getString("sender_hash"),r.getBytes("sender_ciphertext"),r.getBytes("body_ciphertext"),r.getString("disposition")},form.get("MessageSid"));
            if(!old.isEmpty()){var saved=old.getFirst();if(!hash.equals(saved[1])||(saved[2]!=null&&!phone.equals(secrets.decrypt((UUID)saved[0],"SENDER",(byte[])saved[2])))||(saved[3]!=null&&!body.equals(secrets.decrypt((UUID)saved[0],"BODY",(byte[])saved[3]))))throw new PlatformStore.Conflict("INBOUND_MESSAGE_SID_CONFLICT");return (String)saved[4];}
            UUID lead=null;
            jdbc.update("INSERT INTO experience.inbound_message(id,created_by,updated_by,provider,provider_message_id,channel,sender_hash,sender_ciphertext,body_ciphertext,received_at,retention_until,disposition) VALUES (?,'twilio-inbound','twilio-inbound','TWILIO',?,'WHATSAPP',?,?,?, ?,?,'RECEIVED')",message,form.get("MessageSid"),hash,secrets.encrypt(message,"SENDER",phone),secrets.encrypt(message,"BODY",body),Timestamp.from(clock.instant()),Timestamp.from(clock.instant().plus(Duration.ofDays(retentionDays))));
            if(disposition.equals("OPT_OUT")) suppress(hash,message,correlation);
            if(disposition.equals("LEAD_CREATED")) {
                lead=EventCodec.uuid7(clock.instant());
                jdbc.update("INSERT INTO experience.lead(id,created_by,updated_by,type,name,email,subject,message,status,priority,source) VALUES (?,'twilio-inbound','twilio-inbound','CONTACT','WhatsApp contact',NULL,'WhatsApp inquiry','Verified WhatsApp inquiry; see linked private inbound record.','NEW','NORMAL','WHATSAPP_INBOUND')",lead);
                jdbc.update("UPDATE experience.inbound_message SET disposition='LEAD_CREATED',lead_id=?,updated_at=?,updated_by='twilio-inbound',version=version+1 WHERE id=?",lead,Timestamp.from(clock.instant()),message);
                event("lead.created.v1",lead,0,correlation,Map.of("lead_id",lead,"type","CONTACT","priority","NORMAL","recipient_group","SALES"));
            } else jdbc.update("UPDATE experience.inbound_message SET disposition=?,updated_at=?,updated_by='twilio-inbound',version=version+1 WHERE id=?",disposition,Timestamp.from(clock.instant()),message);
            audit.append("twilio-inbound",AuditValues.ActorKind.SERVICE,AuditValues.Action.CONSENT_CHANGE,AuditValues.Resource.NOTIFICATION,message,correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("disposition",disposition,"channel","WHATSAPP"),null);
            if(disposition.equals("OPT_OUT")){var payload=new LinkedHashMap<String,Object>();payload.put("party_id",null);payload.put("destination_hash",hash);payload.put("destination_kind","PHONE");payload.put("email_hash",null);payload.put("channel","WHATSAPP");payload.put("purpose","ALL");payload.put("granted",false);payload.put("revision",1);event("consent.changed.v1",message,1,correlation,payload);}
            return disposition;
        });
        String dispositionResult=Objects.requireNonNullElse(result,"unknown");
        metrics.counter("nigson.messaging.inbound","owner","experience","result",dispositionResult).increment();
        String response=Objects.equals(dispositionResult,"HELP")?"<Message>For assistance, contact Nigson support through official channels.</Message>":"";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>"+response+"</Response>";
    }
    private String command(String body){String value=body.trim().toUpperCase(Locale.ROOT);if(value.equals("STOP")||value.equals("UNSUBSCRIBE"))return "OPT_OUT";if(value.equals("HELP"))return "HELP";return "LEAD_CREATED";}
    /** Erase recoverable sender/message content after configured retention, retaining only keyed dedup identity. */
    public int eraseExpired(int limit){if(limit<1||limit>100)throw ApiValues.invalid();return tx.execute(status->jdbc.update("WITH due AS (SELECT id FROM experience.inbound_message WHERE retention_until<=? AND (sender_ciphertext IS NOT NULL OR body_ciphertext IS NOT NULL) ORDER BY retention_until,id FOR UPDATE SKIP LOCKED LIMIT ?) UPDATE experience.inbound_message i SET sender_ciphertext=NULL,body_ciphertext=NULL,version=i.version+1,updated_at=?,updated_by='inbound-retention' FROM due WHERE i.id=due.id",Timestamp.from(clock.instant()),limit,Timestamp.from(clock.instant())));}
    private void suppress(String hash,UUID id,UUID correlation){
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"destination:"+hash+":WHATSAPP");
        jdbc.update("INSERT INTO experience.communication_suppression(id,created_by,updated_by,channel,reason,suppressed_at,destination_hash,destination_kind,hash_key_version) VALUES (?,'twilio-inbound','twilio-inbound','WHATSAPP','OPT_OUT',? ,?,'PHONE',?) ON CONFLICT(destination_hash,channel) DO NOTHING",id,Timestamp.from(clock.instant()),hash,phones.hashVersion());
        jdbc.update("UPDATE experience.communication_destination SET status='UNSUBSCRIBED',updated_at=?,updated_by='twilio-inbound',version=version+1 WHERE destination_hash=? AND channel='WHATSAPP' AND status IN ('ACTIVE','PENDING')",Timestamp.from(clock.instant()),hash);
        audit.append("twilio-inbound",AuditValues.ActorKind.SERVICE,AuditValues.Action.CONSENT_CHANGE,AuditValues.Resource.NOTIFICATION,id,correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("state","SUPPRESSED","channel","WHATSAPP"),null);
    }
    private void event(String type,UUID aggregate,long version,UUID correlation,Object payload){var node=EventCodec.JSON.createObjectNode();node.put("event_id",EventCodec.uuid7(clock.instant()).toString());node.put("event_type",type);node.put("schema_version",1);node.put("producer","experience");node.put("occurred_at",clock.instant().toString());node.put("aggregate_id",aggregate.toString());node.put("aggregate_version",version);node.putNull("organization_id");node.put("correlation_id",correlation.toString());node.putNull("traceparent");node.set("payload",EventCodec.JSON.valueToTree(payload));store.append(EventCodec.JSON.writeValueAsString(node),"twilio-inbound");}
}
