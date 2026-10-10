package com.nigson.commerce.catalogue;

import java.util.*;
import java.time.Clock;
import tools.jackson.databind.JsonNode;
import com.nigson.commerce.platform.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;

public final class TaxonomyService {
    private final TaxonomyRepository repository;private final CatalogueAuthority authority;private final PlatformStore commands;private final AuditWriter audit;private final Clock clock;
    public TaxonomyService(TaxonomyRepository repository,CatalogueAuthority authority,PlatformStore commands,AuditWriter audit,Clock clock){this.repository=repository;this.authority=authority;this.commands=commands;this.audit=audit;this.clock=clock;}
    public CatalogueValues.Taxonomy write(ActorAssertion.Actor actor,TaxonomyRepository.Kind kind,UUID id,long expected,JsonNode input,String key,UUID correlation){
        authority.require(actor,"CONTENT_EDITOR");boolean create=id==null;ApiValues.idempotencyKey(key);
        if(expected<0||expected==Long.MAX_VALUE||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();
        CatalogueValues.object(input,kind==TaxonomyRepository.Kind.BRAND?Set.of("name","slug","active"):Set.of("name","slug","parentId","sortOrder","active"),false);
        String api=kind==TaxonomyRepository.Kind.BRAND?(create?"API-022":"API-023"):(create?"API-024":"API-025");
        var canonical=EventCodec.JSON.valueToTree(Map.of("id",create?"CREATE":id.toString(),"version",expected,"input",input));
        var response=commands.executeLocal(actor.party().toString(),api,key,canonical,db->{
            repository.serialize();authority.stabilize(actor,"CONTENT_EDITOR");
            var before=create?null:repository.find(kind,id);if(before!=null&&before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            var merged=(tools.jackson.databind.node.ObjectNode)input.deepCopy();
            if(before!=null){var old=EventCodec.JSON.valueToTree(before);for(String field:List.of("name","slug","parentId","sortOrder","active"))if(!merged.has(field)&&old.has(field))merged.set(field,old.get(field));}
            UUID target=create?EventCodec.uuid7(clock.instant()):id;
            var value=new TaxonomyRepository.Record(target,CatalogueValues.string(merged,"name",120,false,false),CatalogueValues.slug(CatalogueValues.string(merged,"slug",140,false,false),140),kind==TaxonomyRepository.Kind.CATEGORY?CatalogueValues.uuid(merged,"parentId",true):null,kind==TaxonomyRepository.Kind.CATEGORY?CatalogueValues.integer(merged,"sortOrder",0):0,CatalogueValues.bool(merged,"active",true),create?0:expected);
            var saved=repository.save(kind,value,actor.party().toString(),create);
            audit.append(actor.party().toString(),AuditValues.ActorKind.USER,create?AuditValues.Action.TAXONOMY_CREATE:AuditValues.Action.TAXONOMY_UPDATE,kind==TaxonomyRepository.Kind.BRAND?AuditValues.Resource.BRAND:AuditValues.Resource.CATEGORY,target,correlation,AuditValues.Reason.OPERATOR_REQUEST,before==null?null:Map.of("version",before.version(),"enabled",before.active()),Map.of("version",saved.version(),"enabled",saved.active()),null);
            var result=new CatalogueValues.Taxonomy(saved.id(),saved.name(),saved.slug(),saved.parentId(),saved.active(),saved.version());
            return new PlatformStore.Response(create?201:200,EventCodec.JSON.valueToTree(result),target);
        });return EventCodec.JSON.treeToValue(response.body(),CatalogueValues.Taxonomy.class);
    }
}
