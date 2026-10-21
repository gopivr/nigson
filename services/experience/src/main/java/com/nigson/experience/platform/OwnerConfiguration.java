package com.nigson.experience.platform;

import java.time.Clock;
import java.text.Normalizer;
import java.util.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.security.PrivateWorkload;
import tools.jackson.databind.JsonNode;

/** API-139 immutable configuration revisions and explicit Experience queue replay. */
public final class OwnerConfiguration {
    public record View(String key, long revision, boolean active, long version) {}
    private final ExperienceConfigurationRepository repository;
    private final PlatformStore commands;
    private final AuditWriter audit;
    private final Clock clock;
    private final Map<String, ExperienceConfigurationPolicy> policies;
    public OwnerConfiguration(ExperienceConfigurationRepository repository, PlatformStore commands,
                              AuditWriter audit, Clock clock, List<ExperienceConfigurationPolicy> registrations) {
        this.repository = repository; this.commands = commands; this.audit = audit; this.clock = clock;
        var map = new HashMap<String, ExperienceConfigurationPolicy>();
        for (var policy : registrations) if (!policy.key().matches("[a-z][a-z0-9.-]{0,119}") || map.put(policy.key(), policy) != null)
            throw new IllegalArgumentException("INVALID_EXPERIENCE_CONFIGURATION_REGISTRY");
        this.policies = Map.copyOf(map);
    }
    private void authorize(PrivateWorkload.Proof proof) {
        var route = PrivateWorkload.ROUTES.stream().filter(r -> r.api().equals("API-139") && r.owner().equals("experience")
                && r.client().equals("nigson-commerce") && r.scope().equals("configuration.write_WITH_SIGNED_ADMIN")).findFirst().orElseThrow(ActorAssertion::denied);
        if (proof == null || !route.equals(proof.route()) || !route.client().equals(proof.client()) || proof.actor() == null)
            throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        var actor = proof.actor();
        if (actor.organization() != null || !actor.roles().contains("ADMIN") || !actor.scopes().contains(route.scope()))
            throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        ActorAssertion.recentMfa(actor, clock);
    }
    public View put(PrivateWorkload.Proof proof, String key, long expected, JsonNode input, String requestKey, UUID correlation) {
        authorize(proof); ActorAssertion.Actor actor = proof.actor();
        if (key == null || !key.matches("[a-z][a-z0-9.-]{0,119}") || expected < 0 || expected == Long.MAX_VALUE || correlation == null || correlation.equals(new UUID(0, 0))) throw ApiValues.invalid();
        var policy = policies.get(key); if (policy == null) throw new Problem.Failure(ProblemCode.PLATFORM_RULE_VIOLATION);
        if (input == null || !input.isObject() || input.size() != 3 || !input.has("value") || !input.has("active") || !input.has("reason")
                || !input.get("value").isObject() || !input.get("active").isBoolean() || !input.get("reason").isString()
                || EventCodec.JSON.writeValueAsBytes(input).length > 65536) throw ApiValues.invalid();
        var reason = input.get("reason").asString();
        if (reason.length() > 500 || reason.strip().length() < 10 || reason.codePoints().anyMatch(Character::isISOControl)
                || !Normalizer.isNormalized(reason, Normalizer.Form.NFC)) throw ApiValues.invalid();
        ApiValues.idempotencyKey(requestKey); JsonNode value = input.get("value"); boolean active = input.get("active").asBoolean();
        policy.validate(value); if (active) ActorAssertion.recentMfa(actor, clock);
        var request = EventCodec.JSON.valueToTree(Map.of("key", key, "version", expected, "input", input));
        var response = commands.executeLocal(actor.party().toString(), "API-139", requestKey, request, db -> {
            repository.serialize(key); authorize(proof);
            var prior = repository.latest(key); long current = prior == null ? 0 : prior.version();
            if (current != expected) throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            long revision = prior == null ? 1 : Math.addExact(prior.revision(), 1); long nextVersion = current + 1;
            UUID id = EventCodec.uuid7(clock.instant());
            ExperienceConfigurationPolicy.AuditChange change = policy.apply(db, value, active, id, revision, nextVersion);
            repository.append(id, key, revision, nextVersion, value, active, actor);
            UUID resourceId = change.resourceId() == null ? id : change.resourceId();
            Map<String, ?> before = change.before() == null ? prior == null ? null : Map.of("key", key,
                    "revision", prior.revision(), "version", prior.version(), "active", prior.active()) : change.before();
            var after = change.after() == null ? Map.<String,Object>of("key", key, "revision", revision, "version", nextVersion, "active", active) : change.after();
            audit.append(actor.party().toString(), AuditValues.ActorKind.USER, change.action(), change.resource(), resourceId,
                    correlation, change.reason(), before, after, null);
            return new PlatformStore.Response(200, EventCodec.JSON.valueToTree(new View(key, revision, active, nextVersion)), id);
        });
        return EventCodec.JSON.treeToValue(response.body(), View.class);
    }
}
