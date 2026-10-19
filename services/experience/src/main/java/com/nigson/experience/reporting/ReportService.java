package com.nigson.experience.reporting;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.AuditValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;
import io.micrometer.core.instrument.MeterRegistry;

/** ANALYST report query with current signed actor trust and an auditable consistent snapshot. */
public final class ReportService {
    private static final UUID REPORT_ID=UUID.nameUUIDFromBytes("experience:API-110".getBytes(StandardCharsets.UTF_8));
    private final JdbcTemplate jdbc; private final ReportRepository repository; private final AuditWriter audit;
    private final MeterRegistry metrics; private final TransactionTemplate reads;
    public ReportService(JdbcTemplate jdbc,ReportRepository repository,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.repository=repository;this.audit=audit;this.metrics=metrics;
        reads=new TransactionTemplate(manager);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(3);
    }
    public ReportValues.Summary engagement(ActorAssertion.Actor actor,ReportValues.Query query){
        if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(actor.organization()!=null||!actor.roles().contains("ANALYST")||!actor.scopes().contains("report.read_WITH_SIGNED_ANALYST"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return reads.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2500ms'");var summary=repository.engagement(query);var wire=WireContext.current();UUID correlation=wire==null?EventCodec.uuid7(java.time.Instant.now()):wire.correlationId;audit.append(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.REPORT_READ,AuditValues.Resource.REPORT,REPORT_ID,correlation,AuditValues.Reason.SECURITY_POLICY,null,Map.of("report","API-110"),null);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){metrics.counter("nigson.reporting.queries","owner","experience","report","engagement").increment();}});return summary;});
    }
}
