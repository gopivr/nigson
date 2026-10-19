package com.nigson.experience.audit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;

/** ADMIN-only consistent read of Experience-owned immutable audit evidence. */
public final class AuditQueryService {
    private final JdbcTemplate jdbc; private final AuditQueryRepository repository; private final TransactionTemplate reads; private final MeterRegistry metrics;
    public AuditQueryService(JdbcTemplate jdbc,AuditQueryRepository repository,PlatformTransactionManager manager,MeterRegistry metrics){this.jdbc=jdbc;this.repository=repository;this.metrics=metrics;reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(3);}
    public AuditValues.Page search(ActorAssertion.Actor actor,AuditValues.Query query){
        if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(actor.organization()!=null||!actor.roles().contains("ADMIN")||!actor.scopes().contains("audit.read_WITH_SIGNED_ADMIN"))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        return reads.execute(status->{jdbc.execute("SET LOCAL statement_timeout='2500ms'");var page=repository.search(query);org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.audit.queries","owner","experience").increment();}});return page;});
    }
}
