package com.nigson.commerce.audit;

import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.commerce.platform.AuditAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.ActorAssertion;

/** ADMIN-only consistent read of Commerce-owned audit history; the query itself is side-effect free. */
public final class AuditQueryService {
    private final AuditAuthority authority;
    private final AuditQueryRepository repository;
    private final TransactionTemplate reads;
    private final MeterRegistry metrics;

    public AuditQueryService(AuditAuthority authority, AuditQueryRepository repository,
                             PlatformTransactionManager manager, MeterRegistry metrics) {
        this.authority = authority;
        this.repository = repository;
        this.metrics = metrics;
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setTimeout(3);
    }

    public AuditValues.Page search(ActorAssertion.Actor actor, AuditValues.Query query) {
        if (actor == null) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        return reads.execute(status -> {
            // Current ADMIN authority is checked in the same repeatable-read snapshot as the audit page.
            authority.requireCurrentAdministrator(actor);
            var page = repository.search(query);
            metrics.counter("nigson.audit.queries", "owner", "commerce").increment();
            return page;
        });
    }
}
