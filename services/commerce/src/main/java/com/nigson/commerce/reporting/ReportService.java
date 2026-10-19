package com.nigson.commerce.reporting;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.commerce.platform.AuditWriter;
import com.nigson.shared.problem.AuditValues;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;

/** Read-only repeatable snapshot with current Commerce staff role checked in the same transaction. */
public final class ReportService {
    private static final java.util.UUID REPORT_ID=java.util.UUID.nameUUIDFromBytes("commerce:API-075".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final JdbcTemplate jdbc; private final ReportRepository repository; private final TransactionTemplate reads; private final MeterRegistry metrics; private final AuditWriter audit;
    public ReportService(JdbcTemplate jdbc,ReportRepository repository,PlatformTransactionManager manager,MeterRegistry metrics,AuditWriter audit){this.jdbc=jdbc;this.repository=repository;this.metrics=metrics;this.audit=audit;reads=new TransactionTemplate(manager);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(3);}
    public ReportValues.Summary commerce(ActorAssertion.Actor actor,ReportValues.Query query){
        if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        return reads.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2500ms'");requireAnalyst(actor);var result=repository.commerce(actor,query);var context=com.nigson.shared.observability.WireContext.current();var correlation=context==null?EventCodec.uuid7(java.time.Instant.now()):context.correlationId;audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.COMMERCE_REPORT_READ,AuditValues.Resource.REPORT,REPORT_ID,correlation,AuditValues.Reason.SECURITY_POLICY,null,java.util.Map.of("report","API-075"),null);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.reporting.queries","owner","commerce","report","commerce").increment();}});return result;});
    }
    private void requireAnalyst(ActorAssertion.Actor actor){
        if(!actor.roles().contains("ANALYST"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        Boolean active=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role='ANALYST' AND r.revoked_at IS NULL)",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject());
        if(!Boolean.TRUE.equals(active))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
}
