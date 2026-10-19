package com.nigson.commerce.cart;

import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Commerce-owned eligibility read and idempotent candidate persistence. */
public final class CartRecoveryService {
    private static final String KEY = "cart-recovery-v1";
    private final JdbcTemplate jdbc;
    private final CartCheckout checkout;
    private final AuditWriter audit;
    private final MeterRegistry metrics;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CartRecoveryService(JdbcTemplate jdbc, CartCheckout checkout, AuditWriter audit,
                               MeterRegistry metrics, PlatformTransactionManager manager, Clock clock) {
        this.jdbc=jdbc; this.checkout=checkout; this.audit=audit; this.metrics=metrics;
        this.clock=clock; tx=new TransactionTemplate(manager); tx.setTimeout(3);
    }

    private static void proof(PrivateWorkload.Proof proof) {
        if(proof==null||proof.actor()!=null||!"nigson-integration".equals(proof.client())||
           proof.subject()==null||!proof.subject().matches("[A-Za-z0-9._:-]{1,100}")||
           !PrivateWorkload.ROUTES.stream().anyMatch(r->r.api().equals("API-159")&&r.equals(proof.route())))
            throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }

    public CartRecoveryValues.Eligibility eligibility(PrivateWorkload.Proof proof, UUID cartId, UUID correlation) {
        proof(proof);
        if(cartId==null||cartId.equals(new UUID(0,0))||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL lock_timeout='500ms'"); jdbc.execute("SET LOCAL statement_timeout='2s'");
            var cart=jdbc.query("SELECT party_id,last_activity_at FROM commerce.cart WHERE id=?",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getTimestamp(2).toInstant()},cartId);
            if(cart.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);
            UUID party=(UUID)cart.getFirst()[0];
            boolean eligible=checkout.recoveryEligible(party,cartId);
            if(eligible) {
                Instant lastActivity=jdbc.queryForObject("SELECT last_activity_at FROM commerce.cart WHERE id=?",(r,n)->r.getTimestamp(1).toInstant(),cartId);
                int inserted=jdbc.update("INSERT INTO commerce.abandoned_cart_candidate(id,created_by,updated_by,cart_id,party_id,scheduled_at,status,notification_key) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(cart_id,notification_key) DO NOTHING",
                    com.nigson.shared.events.EventCodec.uuid7(clock.instant()),"service:nigson-integration","service:nigson-integration",cartId,party,Timestamp.from(lastActivity.plus(Duration.ofHours(24))),"ELIGIBLE",KEY);
                if(inserted==1) {
                    audit.append("service:nigson-integration",AuditValues.ActorKind.SERVICE,AuditValues.Action.CART_TRANSITION,AuditValues.Resource.CART,cartId,correlation,AuditValues.Reason.SECURITY_POLICY,null,Map.of("state","ELIGIBLE","version",0L),null);
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.cart.commands","operation","RECOVERY_CANDIDATE").increment();}});
                }
            }
            return new CartRecoveryValues.Eligibility(eligible,party,null);
        });
    }
}
