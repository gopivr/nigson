package com.nigson.experience.lead;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Minimal owner-authoritative suppression read for the unlocked Integration send boundary. */
public final class LeadMessagingSuppressionCheck {
    public record Result(boolean suppressed) {}
    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final MeterRegistry metrics;
    private final TransactionTemplate tx;

    public LeadMessagingSuppressionCheck(JdbcTemplate jdbc, AuditWriter audit, MeterRegistry metrics, PlatformTransactionManager manager) {
        this.jdbc=Objects.requireNonNull(jdbc);this.audit=Objects.requireNonNull(audit);this.metrics=Objects.requireNonNull(metrics);
        this.tx=new TransactionTemplate(Objects.requireNonNull(manager));this.tx.setTimeout(3);
    }

    public Result read(PrivateWorkload.Proof proof, String hash, String channel, UUID correlation) {
        var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals("API-205")&&r.owner().equals("experience")&&r.client().equals("nigson-integration")).findFirst().orElseThrow(ActorAssertion::denied);
        if(proof==null||!route.equals(proof.route())||proof.actor()!=null||!route.client().equals(proof.client()))throw ActorAssertion.denied();
        if(hash==null||!hash.matches("[a-f0-9]{64}")||!"WHATSAPP".equals(channel)||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        return tx.execute(status->{
            jdbc.execute("SET LOCAL statement_timeout='2s'");
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"destination:"+hash+":WHATSAPP");
            boolean suppressed=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM experience.communication_suppression WHERE destination_hash=? AND channel='WHATSAPP')",Boolean.class,hash));
            UUID auditResource=UUID.nameUUIDFromBytes(("whatsapp-suppression:"+hash).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            audit.append("nigson-integration",AuditValues.ActorKind.SERVICE,AuditValues.Action.NOTIFICATION_DELIVERY,AuditValues.Resource.NOTIFICATION,auditResource,correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("context","STAFF_REPLY_SUPPRESSION_CHECK","channel","WHATSAPP","suppressed",suppressed),null);
            metrics.counter("nigson.messaging.suppression.check","owner","experience","result",suppressed?"suppressed":"clear").increment();
            return new Result(suppressed);
        });
    }
}
