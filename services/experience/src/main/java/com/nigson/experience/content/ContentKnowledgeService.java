package com.nigson.experience.content;

import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.experience.platform.AuditWriter;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.AuditValues;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.security.PrivateWorkload;

public final class ContentKnowledgeService {
    private final JdbcTemplate jdbc;
    private final ContentKnowledgeRepository repository;
    private final AuditWriter audit;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;

    public ContentKnowledgeService(JdbcTemplate jdbc, ContentKnowledgeRepository repository, AuditWriter audit,
                                   PlatformTransactionManager manager, MeterRegistry metrics) {
        this.jdbc=jdbc; this.repository=repository; this.audit=audit; this.metrics=metrics;
        tx=new TransactionTemplate(manager); tx.setTimeout(3); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public ContentKnowledgeValues.ValidationResult validate(PrivateWorkload.Proof proof,
                                                             ContentKnowledgeValues.ValidationInput input,
                                                             UUID correlation) {
        require(proof, "API-125", "knowledge.validate");
        if (input==null || input.items()==null || input.items().isEmpty() || input.items().size()>20 || correlation==null)
            throw ApiValues.invalid();
        var seen=new java.util.HashSet<String>();
        for (var item:input.items()) if (item==null || item.contentItemId()==null || item.revision()<1
                || !seen.add(item.contentItemId()+":"+item.revision())) throw ApiValues.invalid();
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout='2500ms'");
            var results=input.items().stream().map(item -> repository.validate(item.contentItemId(), item.revision())).toList();
            for (var item:input.items()) audit.append("workload:nigson-ai", AuditValues.ActorKind.SERVICE,
                    AuditValues.Action.CONTENT_REVISION_VALIDATE, AuditValues.Resource.CONTENT,
                    item.contentItemId(), correlation, null, null,
                    java.util.Map.of("revision", item.revision()), null);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { metrics.counter("nigson.content.knowledge.queries", "owner", "experience", "api", "API-125").increment(); }
                    });
            return new ContentKnowledgeValues.ValidationResult(results);
        });
    }

    public ContentKnowledgeValues.ContentKnowledge read(PrivateWorkload.Proof proof, UUID id, UUID correlation) {
        require(proof, "API-126", "knowledge.read");
        if (id==null || correlation==null) throw ApiValues.invalid();
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout='2500ms'");
            var content=repository.current(id);
            if (content==null) throw new Problem.Failure(ProblemCode.NOT_FOUND);
            audit.append("workload:nigson-ai", AuditValues.ActorKind.SERVICE,
                    AuditValues.Action.CONTENT_KNOWLEDGE_READ, AuditValues.Resource.CONTENT,
                    id, correlation, null, null, java.util.Map.of("revision", content.revision(), "checksum", content.checksum()), null);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { metrics.counter("nigson.content.knowledge.queries", "owner", "experience", "api", "API-126").increment(); }
                    });
            return content;
        });
    }

    private static void require(PrivateWorkload.Proof proof, String api, String scope) {
        if (proof==null || proof.actor()!=null || proof.route()==null || !proof.route().api().equals(api)
                || !proof.route().owner().equals("experience") || !proof.route().client().equals("nigson-ai")
                || !proof.route().scope().equals(scope) || !proof.client().equals("nigson-ai"))
            throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
}
