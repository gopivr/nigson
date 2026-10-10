package com.nigson.commerce.files;

import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;
import io.micrometer.core.instrument.MeterRegistry;

/** Durable local intent -> unlocked provider -> owned atomic outcome. */
public final class FileService {
    private final FileRepository files;private final FileStorage storage;private final PlatformStore commands;private final AuditWriter audit;private final TransactionTemplate tx;private final Clock clock;private final String env;private final MeterRegistry metrics;
    public FileService(FileRepository files,FileStorage storage,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager,Clock clock,String env,MeterRegistry metrics){this.files=files;this.storage=storage;this.commands=commands;this.audit=audit;this.tx=new TransactionTemplate(manager);this.tx.setTimeout(3);this.clock=clock;this.env=env;this.metrics=metrics;}
    private void enabled(){if(!storage.configured())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);}
    private void request(String key,UUID correlation){ApiValues.idempotencyKey(key);if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();}
    private JsonNode canonical(UUID id,long expected,JsonNode body){if(expected<0||expected==Long.MAX_VALUE)throw ApiValues.invalid();return EventCodec.JSON.valueToTree(Map.of("id",id,"version",expected,"body",body));}
    private FileValues.Asset dto(FileRepository.Stored v){return new FileValues.Asset(v.id(),v.state(),v.purpose(),v.mime(),v.size(),v.checksum(),null,v.version());}
    private void evidence(String actor,AuditValues.ActorKind kind,AuditValues.Action action,FileRepository.Stored before,FileRepository.Stored after,UUID correlation){audit.append(actor,kind,action,AuditValues.Resource.FILE,after.id(),correlation,kind==AuditValues.ActorKind.USER?AuditValues.Reason.OPERATOR_REQUEST:AuditValues.Reason.VERIFIED_PROVIDER,before==null?null:Map.of("state",before.state(),"version",before.version()),Map.of("state",after.state(),"version",after.version()),null);}
    private void matches(FileRepository.Stored v,FileStorage.ObjectInfo info){if(info==null||info.size()!=v.size()||!Objects.equals(info.mimeType(),v.mime())||!Objects.equals(info.checksum(),v.checksum())||!Objects.equals(info.owner(),v.owner().toString())||!info.encrypted())throw FileValues.rule();}
    public FileValues.UploadResult upload(ActorAssertion.Actor actor,JsonNode body,String key,UUID correlation){
        files.authorize(actor,"CONTENT_EDITOR",false);request(key,correlation);var input=FileValues.upload(body);String principal=actor.party().toString();enabled();
        if(commands.completed(principal,"API-111",key))return EventCodec.JSON.treeToValue(commands.executeLocal(principal,"API-111",key,body,db->{throw new IllegalStateException("COMPLETED_COMMAND_REQUIRED");}).body(),FileValues.UploadResult.class);
        var prepared=commands.executeLocal(principal,"API-111-PREPARE",key,body,db->{
            UUID id=EventCodec.uuid7(clock.instant());var v=files.create(id,actor,input,FileValues.key(env,input.purpose(),id,0,input.extension()),FileValues.key(env,input.purpose(),id,1,input.extension()),clock.instant().plusSeconds(300));
            evidence(principal,AuditValues.ActorKind.USER,AuditValues.Action.FILE_UPLOAD,null,v,correlation);
            return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(v),id);
        });
        var v=files.find(prepared.resource(),false);files.access(actor,v,false);
        var signed=storage.upload(v.id(),v.sourceKey(),input,principal,v.uploadExpires().minusSeconds(300));
        var response=commands.executeLocal(principal,"API-111",key,body,db->{files.authorize(actor,"CONTENT_EDITOR",true);var current=files.find(v.id(),true);files.access(actor,current,false);if(!current.state().equals("UPLOADING"))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);return new PlatformStore.Response(201,EventCodec.JSON.valueToTree(new FileValues.UploadResult(v.id(),signed.url(),signed.headers(),signed.expiresAt())),v.id());});
        return EventCodec.JSON.treeToValue(response.body(),FileValues.UploadResult.class);
    }
    public FileValues.Asset complete(ActorAssertion.Actor actor,UUID id,long expected,JsonNode body,String key,UUID correlation){
        files.authorize(actor,"CONTENT_EDITOR",false);request(key,correlation);String checksum=FileValues.completion(body);enabled();var request=canonical(id,expected,body);var principal=actor.party().toString();
        var before=files.find(id,false);files.access(actor,before,false);if(!before.checksum().equals(checksum))throw FileValues.rule();
        if(commands.completed(principal,"API-112",key))return EventCodec.JSON.treeToValue(commands.executeLocal(principal,"API-112",key,request,db->{throw new IllegalStateException("COMPLETED_COMMAND_REQUIRED");}).body(),FileValues.Asset.class);
        if(before.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);if(!before.state().equals("UPLOADING")||before.sourceKey()==null)throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
        // Persisted UPLOADING transfer is the durable HEAD intent. HEAD has no side effect.
        var info=storage.quarantine(before.sourceKey());matches(before,info);
        var result=commands.executeLocal(principal,"API-112",key,request,db->{
            files.authorize(actor,"CONTENT_EDITOR",true);var current=files.find(id,true);files.access(actor,current,false);
            if(current.version()!=expected)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);if(!current.state().equals("UPLOADING"))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
            matches(current,info);UUID command=EventCodec.uuid7(clock.instant());var after=files.quarantined(current,info,command,principal);
            var event=new LinkedHashMap<String,Object>();event.put("event_id",command);event.put("event_type","file.scan.requested.v1");event.put("schema_version",1);event.put("producer","commerce");event.put("occurred_at",clock.instant());event.put("aggregate_id",id);event.put("aggregate_version",after.version());event.put("organization_id",null);event.put("correlation_id",correlation);var span=io.opentelemetry.api.trace.Span.current().getSpanContext();event.put("traceparent",span.isValid()?"00-"+span.getTraceId()+"-"+span.getSpanId()+"-"+span.getTraceFlags().asHex():null);event.put("payload",Map.of("owner_service","commerce","asset_id",id,"checksum_sha256",checksum));commands.append(EventCodec.JSON.writeValueAsString(event),principal);
            evidence(principal,AuditValues.ActorKind.USER,AuditValues.Action.FILE_COMPLETION,current,after,correlation);return new PlatformStore.Response(202,EventCodec.JSON.valueToTree(dto(after)),id);
        });return EventCodec.JSON.treeToValue(result.body(),FileValues.Asset.class);
    }
    private String scanner(PrivateWorkload.Proof proof){
        if(proof==null||!proof.client().equals("nigson-integration")||!proof.route().owner().equals("commerce")||!proof.route().api().equals("API-132")||!proof.route().scope().equals("files.scan.write")||proof.actor()!=null||proof.subject()==null||proof.subject().isBlank())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return "scanner:"+proof.client();
    }
    private void binding(FileRepository.Stored v,FileValues.Scan scan,String hash,long expected){
        if(v.owner()==null||!Set.of("IMAGE","BROCHURE").contains(v.purpose())||!scan.commandId().equals(v.scanCommand())||!scan.checksumSha256().equals(v.checksum())||v.sourceVersion()==null||v.sourceEtag()==null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        if(v.scanHash()!=null&&!v.scanHash().equals(hash))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);
        if(expected!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
    }
    public FileValues.Asset scan(PrivateWorkload.Proof proof,UUID id,long expected,JsonNode body,String key,UUID correlation){
        String actor=scanner(proof);enabled();request(key,correlation);var input=FileValues.scan(body);String hash=EventCodec.hash(body);var request=canonical(id,expected,body);
        var initial=files.find(id,false);binding(initial,input,hash,expected);
        if(commands.completed(actor,"API-132",key))return EventCodec.JSON.treeToValue(commands.executeLocal(actor,"API-132",key,request,db->{throw new IllegalStateException("COMPLETED_COMMAND_REQUIRED");}).body(),FileValues.Asset.class);
        var prepared=tx.execute(status->{
            var current=files.find(id,true);binding(current,input,hash,expected);
            if(Set.of("CLEAN","PUBLISHED","REJECTED").contains(current.state()))return current;
            if(!current.state().equals("QUARANTINED"))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
            if(current.transferState().equals("COPYING")){
                if(current.lease()!=null&&current.lease().isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_IN_PROGRESS);
                files.unknown(id,hash,actor);return files.find(id,false);
            }
            if(current.transferState().equals("UNKNOWN"))return current;
            var claimed=files.claim(current,input,hash,clock.instant().plusSeconds(15),actor);
            evidence(actor,AuditValues.ActorKind.SERVICE,AuditValues.Action.FILE_PROMOTION_REQUEST,current,claimed,correlation);return claimed;
        });
        if(!Set.of("CLEAN","PUBLISHED","REJECTED").contains(prepared.state())&&input.result().equals("CLEAN")){
            try{
                if(prepared.transferState().equals("UNKNOWN")){
                    var finalInfo=storage.finalObject(prepared.finalKey(),prepared.classification().equals("PRIVATE"));
                    if(finalInfo==null)throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);matches(prepared,finalInfo);
                }else{
                    storage.promote(prepared.sourceKey(),prepared.sourceVersion(),prepared.sourceEtag(),prepared.finalKey(),prepared.classification().equals("PRIVATE"));
                    matches(prepared,storage.finalObject(prepared.finalKey(),prepared.classification().equals("PRIVATE")));
                }
            }catch(RuntimeException uncertain){
                tx.executeWithoutResult(status->{files.find(id,true);files.unknown(id,hash,actor);audit.append(actor,AuditValues.ActorKind.SERVICE,AuditValues.Action.FILE_TRANSFER_UNKNOWN,AuditValues.Resource.FILE,id,correlation,AuditValues.Reason.RECONCILIATION,null,Map.of("state","UNKNOWN"),null);});
                metrics.counter("nigson.files.transfer","outcome","UNKNOWN").increment();throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);
            }
        }
        var result=commands.executeLocal(actor,"API-132",key,request,db->{
            var current=files.find(id,true);binding(current,input,hash,expected);
            if(!Set.of("CLEAN","PUBLISHED","REJECTED").contains(current.state())){var after=files.finish(current,input.result(),actor);evidence(actor,AuditValues.ActorKind.SERVICE,AuditValues.Action.FILE_SCAN_RESULT,current,after,correlation);current=after;}
            return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(dto(current)),id);
        });metrics.counter("nigson.files.transfer","outcome",input.result()).increment();return EventCodec.JSON.treeToValue(result.body(),FileValues.Asset.class);
    }
    public FileValues.Download download(ActorAssertion.Actor actor,UUID id,UUID correlation){
        if(correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();var before=files.find(id,false);files.access(actor,before,false);enabled();
        if(!Set.of("CLEAN","PUBLISHED").contains(before.state()))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);
        var authorized=tx.execute(status->{files.authorize(actor,before.purpose().equals("SHIPPING_LABEL")?"OPERATIONS":"CONTENT_EDITOR",true);var current=files.find(id,true);files.access(actor,current,false);if(!Set.of("CLEAN","PUBLISHED").contains(current.state()))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);evidence(actor.party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.PRIVATE_FILE_DOWNLOAD,current,current,correlation);return current;});
        var signed=storage.download(authorized.key(),authorized.classification().equals("PRIVATE"),clock.instant());return new FileValues.Download(signed.url(),signed.expiresAt());
    }
}
