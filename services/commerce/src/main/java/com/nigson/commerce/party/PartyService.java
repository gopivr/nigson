package com.nigson.commerce.party;
import java.util.*;
import java.time.*;
import tools.jackson.databind.JsonNode;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.platform.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;
public final class PartyService {
    private final PartyRepository repository;private final PlatformStore commands;private final TransactionTemplate transactions;private final AuditWriter audit;private final Clock clock;
    public PartyService(PartyRepository repository,PlatformStore commands,TransactionTemplate transactions,AuditWriter audit,Clock clock){this.repository=repository;this.commands=commands;this.transactions=transactions;this.audit=audit;this.clock=clock;}
    private void actor(ActorAssertion.Actor actor){repository.requireCurrent(actor,false);}
    private void evidence(ActorAssertion.Actor actor,AuditValues.Action action,AuditValues.Resource resource,UUID id,UUID correlation,long previous,long updated){audit.append(actor.party().toString(),AuditValues.ActorKind.USER,action,resource,id,correlation,AuditValues.Reason.OPERATOR_REQUEST,Map.of("version",previous),Map.of("version",updated),null);}
    public PartyValues.Profile profile(ActorAssertion.Actor actor){actor(actor);return transactions.execute(s->{repository.requireCurrent(actor,false);return repository.profile(actor.party());});}
    public List<PartyValues.Address> addresses(ActorAssertion.Actor actor){actor(actor);return transactions.execute(s->{repository.requireCurrent(actor,false);return repository.addresses(actor.party());});}
    public PartyValues.Profile patchProfile(ActorAssertion.Actor actor,long version,JsonNode input,String key,UUID correlation){
        actor(actor);var patch=PartyValues.profilePatch(input);ApiValues.idempotencyKey(key);if(version<0)throw ApiValues.invalid();
        var request=EventCodec.JSON.valueToTree(Map.of("version",version,"patch",patch));
        var response=commands.executeLocal(actor.party().toString(),"API-006",key,request,jdbc->{repository.requireCurrent(actor,true);var before=repository.profile(actor.party());repository.patchProfile(actor.party(),version,patch);var after=repository.profile(actor.party());if(after.version()!=before.version())evidence(actor,AuditValues.Action.PROFILE_UPDATE,AuditValues.Resource.PROFILE,actor.party(),correlation,before.version(),after.version());return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(after),actor.party());});
        return EventCodec.JSON.treeToValue(response.body(),PartyValues.Profile.class);
    }
    public PartyValues.Address createAddress(ActorAssertion.Actor actor,JsonNode input,String key,UUID correlation){
        actor(actor);var data=PartyValues.address(input,null);ApiValues.idempotencyKey(key);
        var response=commands.executeLocal(actor.party().toString(),"API-008",key,EventCodec.JSON.valueToTree(data),jdbc->{
            repository.requireCurrent(actor,true);repository.requireOrganization(actor.party(),data.organizationId(),true);repository.validateCountry(data);
            UUID id=EventCodec.uuid7(clock.instant());var created=new PartyValues.Address(id,data.label(),data.recipient(),data.phone(),data.line1(),data.line2(),data.city(),data.region(),data.postalCode(),data.countryCode(),data.organizationId(),data.isDefault(),0);
            repository.insertAddress(actor.party(),created,correlation);evidence(actor,AuditValues.Action.ADDRESS_CREATE,AuditValues.Resource.ADDRESS,id,correlation,0,0);return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(created),id);
        });return EventCodec.JSON.treeToValue(response.body(),PartyValues.Address.class);
    }
    public PartyValues.Address patchAddress(ActorAssertion.Actor actor,UUID id,long version,JsonNode patch,String key,UUID correlation){
        actor(actor);PartyValues.object(patch,PartyValues.ADDRESS_FIELDS,false);ApiValues.idempotencyKey(key);if(version<0)throw ApiValues.invalid();
        transactions.executeWithoutResult(s->{repository.requireCurrent(actor,false);repository.address(actor.party(),id);});
        var response=commands.executeLocal(actor.party().toString(),"API-009",key,EventCodec.JSON.valueToTree(Map.of("id",id,"version",version,"patch",patch)),jdbc->{repository.requireCurrent(actor,true);var before=repository.address(actor.party(),id);if(before.version()!=version)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);var after=PartyValues.address(patch,before);repository.requireOrganization(actor.party(),after.organizationId(),true);repository.validateCountry(after);
            if(!before.equals(after)){repository.patchAddress(actor.party(),before,after,correlation);evidence(actor,AuditValues.Action.ADDRESS_UPDATE,AuditValues.Resource.ADDRESS,id,correlation,before.version(),before.version()+1);}
            return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(repository.address(actor.party(),id)),id);
        });return EventCodec.JSON.treeToValue(response.body(),PartyValues.Address.class);
    }
    public void deleteAddress(ActorAssertion.Actor actor,UUID id,long version,String key,UUID correlation){
        actor(actor);ApiValues.idempotencyKey(key);if(version<0)throw ApiValues.invalid();
        commands.executeLocal(actor.party().toString(),"API-010",key,EventCodec.JSON.valueToTree(Map.of("id",id,"version",version)),jdbc->{repository.requireCurrent(actor,true);var before=repository.address(actor.party(),id);if(before.version()!=version)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);repository.deleteAddress(actor.party(),before);evidence(actor,AuditValues.Action.ADDRESS_DELETE,AuditValues.Resource.ADDRESS,id,correlation,before.version(),before.version()+1);return new PlatformStore.Response(204,EventCodec.JSON.nullNode(),id);});
    }
    public UUID provision(PartyValues.VerifiedIdentity identity){return transactions.execute(s->repository.provision(identity));}
}
