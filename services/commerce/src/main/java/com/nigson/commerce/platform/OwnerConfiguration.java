package com.nigson.commerce.platform;

import java.time.Clock;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;

/** Append-only configuration revisions; ADMIN authorization is checked against owned state. */
public final class OwnerConfiguration {
    public record View(String key, long revision, boolean active, long version) {}
    private final ConfigurationRepository repository;
    private final PlatformStore commands;
    private final AuditWriter audit;
    private final Clock clock;
    private final Map<String, ConfigurationPolicy> policies;
    public OwnerConfiguration(ConfigurationRepository repository, PlatformStore commands, AuditWriter audit,
            Clock clock, List<ConfigurationPolicy> registrations) {
        this.repository=repository;this.commands=commands;this.audit=audit;this.clock=clock;
        var map=new HashMap<String,ConfigurationPolicy>();
        for(var policy:registrations) {
            if(!policy.key().matches("[a-z][a-z0-9.-]{0,119}")||map.put(policy.key(),policy)!=null)
                throw new IllegalArgumentException("INVALID_CONFIGURATION_REGISTRY");
        }
        policies=Map.copyOf(map);
    }
    private void authorize(ActorAssertion.Actor actor) {
        if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        repository.requireAdministrator(actor);
    }
    public View put(ActorAssertion.Actor actor,String key,long expected,JsonNode input,String requestKey,UUID correlation) {
        authorize(actor);
        if(key==null||!key.matches("[a-z][a-z0-9.-]{0,119}")||expected<0||expected==Long.MAX_VALUE||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        var policy=policies.get(key);if(policy==null)throw new Problem.Failure(ProblemCode.PLATFORM_RULE_VIOLATION);
        if(input==null||!input.isObject()||input.size()!=3||!input.has("value")||!input.has("active")||!input.has("reason")||!input.get("value").isObject()||!input.get("active").isBoolean()||!input.get("reason").isString())throw ApiValues.invalid();
        String reason=input.get("reason").asString();
        if(reason.length()>500||reason.strip().length()<10||reason.codePoints().anyMatch(Character::isISOControl)||!java.text.Normalizer.isNormalized(reason,java.text.Normalizer.Form.NFC)||EventCodec.JSON.writeValueAsBytes(input).length>65536)throw ApiValues.invalid();
        ApiValues.idempotencyKey(requestKey);boolean active=input.get("active").asBoolean();
        policy.validate(input.get("value"));if(active)ActorAssertion.recentMfa(actor,clock);
        var request=EventCodec.JSON.valueToTree(Map.of("key",key,"version",expected,"input",input));
        var response=commands.executeLocal(actor.party().toString(),"API-135",requestKey,request,db->{
            repository.serialize(key,actor.party());
            authorize(actor);if(active)ActorAssertion.recentMfa(actor,clock);
            var prior=repository.latest(key);
            long version=prior==null?0:prior.version();
            if(version!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            long revision=prior==null?1:Math.addExact(prior.revision(),1);
            if(active)policy.activate(db,input.get("value"));
            String subject=actor.party().toString();
            UUID aggregate=repository.append(key,revision,version+1,input.get("value"),active,actor);
            var before=prior==null?null:Map.<String,Object>of("revision",prior.revision(),"version",version,"enabled",prior.active());
            audit.append(subject,AuditValues.ActorKind.USER,AuditValues.Action.FEATURE_ACTIVATION,AuditValues.Resource.FEATURE,aggregate,correlation,AuditValues.Reason.CONFIGURATION_CHANGE,before,Map.of("revision",revision,"version",version+1,"enabled",active),null);
            return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(new View(key,revision,active,version+1)),aggregate);
        });
        return EventCodec.JSON.treeToValue(response.body(),View.class);
    }
}
