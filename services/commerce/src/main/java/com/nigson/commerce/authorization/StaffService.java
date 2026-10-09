package com.nigson.commerce.authorization;

import java.util.*;
import java.time.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.platform.PlatformStore;
import com.nigson.commerce.security.JdbcSessionTrust;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.events.EventCodec;

public final class StaffService {
    private final StaffRepository repository;
    private final PlatformStore commands;
    private final JdbcSessionTrust sessions;
    private final TransactionTemplate queries;
    private final Clock clock;
    public StaffService(StaffRepository repository, PlatformStore commands, JdbcSessionTrust sessions,
            TransactionTemplate queries, Clock clock) {
        this.repository = repository; this.commands = commands; this.sessions = sessions;
        this.queries = queries; this.clock = clock;
    }
    private void authorize(ActorAssertion.Actor actor, boolean mutation) {
        if (actor == null) throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        repository.requireAdministrator(actor);
        if (mutation) ActorAssertion.recentMfa(actor, clock);
    }
    public Staff.Page search(ActorAssertion.Actor actor, String q, int page, int size, String sort) {
        authorize(actor, false); ApiValues.page(page, size);
        if (q == null || q.length() > 200 || q.codePoints().anyMatch(Character::isISOControl)) throw ApiValues.invalid();
        return queries.execute(s -> { authorize(actor, false); return repository.search(q, page, size, sort); });
    }
    public Staff.View change(ActorAssertion.Actor actor, UUID target, long version, Staff.Change change, String key, UUID correlation) {
        authorize(actor, true);
        if (target == null || target.equals(new UUID(0, 0)) || version < 0 || change == null || correlation == null || correlation.equals(new UUID(0, 0))) throw ApiValues.invalid();
        ApiValues.idempotencyKey(key);
        var request = EventCodec.JSON.valueToTree(Map.of("target", target, "version", version, "roles", change.roles(), "reason", change.reason()));
        var response = commands.executeLocal(actor.party().toString(), "API-012", key, request, jdbc -> {
            repository.serializeChanges(); repository.lockParties(actor.party(), target);
            authorize(actor, true);
            var before = repository.find(target);
            if (before.version() != version) throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            if (before.roles().contains(Staff.Role.ADMIN) && !change.roles().contains(Staff.Role.ADMIN)
                    && repository.activeAdministrators() <= 1) throw new Problem.Failure(ProblemCode.AUTHORIZATION_RULE_VIOLATION);
            if (!new HashSet<>(before.roles()).equals(new HashSet<>(change.roles()))) {
                repository.replace(target, change, before, actor, correlation);
                sessions.revokeSubject(target);
            }
            return new PlatformStore.Response(200, EventCodec.JSON.valueToTree(repository.find(target)), target);
        });
        return EventCodec.JSON.treeToValue(response.body(), Staff.View.class);
    }
}
