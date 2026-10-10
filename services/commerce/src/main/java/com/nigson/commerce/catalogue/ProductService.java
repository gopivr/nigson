package com.nigson.commerce.catalogue;

import java.util.*;
import java.time.Clock;
import tools.jackson.databind.JsonNode;
import com.nigson.commerce.platform.*;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;

public final class ProductService {
    private final ProductRepository products;private final TaxonomyRepository taxonomy;private final CatalogueAuthority authority;private final PlatformStore commands;private final AuditWriter audit;private final Clock clock;
    public ProductService(ProductRepository products,TaxonomyRepository taxonomy,CatalogueAuthority authority,PlatformStore commands,AuditWriter audit,Clock clock){this.products=products;this.taxonomy=taxonomy;this.authority=authority;this.commands=commands;this.audit=audit;this.clock=clock;}
    private void input(ActorAssertion.Actor actor,String role,long expected,String key,UUID correlation){authority.require(actor,role);ApiValues.idempotencyKey(key);if(expected<0||expected==Long.MAX_VALUE||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();}
    private void locks(ActorAssertion.Actor actor,String role){taxonomy.serialize();products.serializeMetadata();authority.stabilize(actor,role);}
    private void event(ProductRepository.Snapshot v,String actor,UUID correlation){
        var payload=Map.of("product_id",v.id(),"revision",v.revision(),"publication_state",v.status(),"market_codes",products.eligibleMarkets(v.id()));
        var envelope=new LinkedHashMap<String,Object>();envelope.put("event_id",EventCodec.uuid7(clock.instant()));envelope.put("event_type","catalogue.product.changed.v1");envelope.put("schema_version",1);envelope.put("producer","commerce");envelope.put("occurred_at",clock.instant());envelope.put("aggregate_id",v.id());envelope.put("aggregate_version",v.version());envelope.put("organization_id",null);envelope.put("correlation_id",correlation);var span=io.opentelemetry.api.trace.Span.current().getSpanContext();envelope.put("traceparent",span.isValid()?"00-"+span.getTraceId()+"-"+span.getSpanId()+"-"+span.getTraceFlags().asHex():null);envelope.put("payload",payload);
        commands.append(EventCodec.JSON.writeValueAsString(envelope),actor);
    }
    private void evidence(ActorAssertion.Actor actor,AuditValues.Action action,UUID id,ProductRepository.Snapshot before,ProductRepository.Snapshot after,UUID correlation){audit.append(actor.party().toString(),AuditValues.ActorKind.USER,action,AuditValues.Resource.PRODUCT,id,correlation,AuditValues.Reason.OPERATOR_REQUEST,before==null?null:Map.of("version",before.version(),"revision",before.revision(),"state",before.status()),Map.of("version",after.version(),"revision",after.revision(),"state",after.status()),null);}
    public CatalogueValues.ProductAdmin write(ActorAssertion.Actor actor,UUID id,long expected,JsonNode body,String key,UUID correlation){
        input(actor,"CONTENT_EDITOR",expected,key,correlation);boolean create=id==null;CatalogueValues.object(body,CatalogueValues.PRODUCT_FIELDS,false);
        var canonical=EventCodec.JSON.valueToTree(Map.of("id",create?"CREATE":id.toString(),"version",expected,"body",body));
        var response=commands.executeLocal(actor.party().toString(),create?"API-017":"API-018",key,canonical,db->{
            locks(actor,"CONTENT_EDITOR");var before=create?null:products.find(id,true);
            if(before!=null&&before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            UUID target=create?EventCodec.uuid7(clock.instant()):id;var values=CatalogueValues.product(body,before==null?null:before.input());
            var updated=products.save(target,values,actor.party().toString(),create);
            evidence(actor,create?AuditValues.Action.PRODUCT_CREATE:AuditValues.Action.PRODUCT_UPDATE,target,before,updated,correlation);event(updated,actor.party().toString(),correlation);
            return new PlatformStore.Response(create?201:200,EventCodec.JSON.valueToTree(products.admin(target)),target);
        });return EventCodec.JSON.treeToValue(response.body(),CatalogueValues.ProductAdmin.class);
    }
    public CatalogueValues.ProductAdmin publish(ActorAssertion.Actor actor,UUID id,long expected,JsonNode body,String key,UUID correlation){
        input(actor,"CONTENT_PUBLISHER",expected,key,correlation);CatalogueValues.object(body,Set.of("action","reason"),false);
        if(body.size()!=2)throw ApiValues.invalid();String action=CatalogueValues.string(body,"action",7,false,false),reason=CatalogueValues.string(body,"reason",500,false,false);
        if(!Set.of("PUBLISH","ARCHIVE").contains(action)||reason.length()<10)throw ApiValues.invalid();
        var canonical=EventCodec.JSON.valueToTree(Map.of("id",id,"version",expected,"body",body));
        var response=commands.executeLocal(actor.party().toString(),"API-019",key,canonical,db->{
            locks(actor,"CONTENT_PUBLISHER");var before=products.find(id,true);if(before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            String target=action.equals("PUBLISH")?"ACTIVE":"ARCHIVED";
            if(!before.status().equals(target)){
                if(target.equals("ACTIVE")){
                    if(!products.publicationReady(id))throw new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);
                    if(before.input().divisionCode()!=null||!before.input().specifications().isEmpty())products.rules().specifications(before.input().divisionCode(),before.input().specifications());
                }
                var after=products.publication(id,target,actor.party().toString());
                evidence(actor,target.equals("ACTIVE")?AuditValues.Action.CONTENT_PUBLICATION:AuditValues.Action.CONTENT_WITHDRAWAL,id,before,after,correlation);event(after,actor.party().toString(),correlation);
            }
            return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(products.admin(id)),id);
        });return EventCodec.JSON.treeToValue(response.body(),CatalogueValues.ProductAdmin.class);
    }
    public CatalogueValues.SkuAdmin sku(ActorAssertion.Actor actor,UUID id,boolean create,long expected,JsonNode body,String key,UUID correlation){
        input(actor,"CONTENT_EDITOR",expected,key,correlation);
        var canonical=EventCodec.JSON.valueToTree(Map.of("id",id,"version",expected,"body",body));
        var response=commands.executeLocal(actor.party().toString(),create?"API-020":"API-021",key,canonical,db->{
            locks(actor,"CONTENT_EDITOR");UUID product=create?id:products.skuProduct(id);var before=products.find(product,true);
            if(create&&before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
            var changed=create?products.createSku(product,body,actor.party().toString()):products.patchSku(id,expected,body,actor.party().toString());
            audit.append(actor.party().toString(),AuditValues.ActorKind.USER,create?AuditValues.Action.SKU_CREATE:AuditValues.Action.SKU_UPDATE,AuditValues.Resource.SKU,changed.id(),correlation,AuditValues.Reason.OPERATOR_REQUEST,create?null:Map.of("version",expected),Map.of("version",changed.version()),null);
            if(!create)event(products.find(product,false),actor.party().toString(),correlation);
            return new PlatformStore.Response(create?201:200,EventCodec.JSON.valueToTree(changed),changed.id());
        });return EventCodec.JSON.treeToValue(response.body(),CatalogueValues.SkuAdmin.class);
    }
}
