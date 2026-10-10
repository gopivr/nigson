package com.nigson.commerce.files;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;

public final class JdbcFileRepository implements FileRepository {
    private final JdbcTemplate jdbc;private final Clock clock;
    public JdbcFileRepository(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public void bound(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");}
    public void authorize(ActorAssertion.Actor a,String role,boolean lock){
        if(a==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(lock){bound();jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),a.party());}
        if(!Set.of("CONTENT_EDITOR","OPERATIONS").contains(role)||a.organization()!=null||!a.roles().contains(role)||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role=? AND r.revoked_at IS NULL)",Boolean.class,a.party(),a.identityIssuer(),a.identitySubject(),role)))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
    public Link linked(UUID resource,boolean lock){
        if(resource==null)return new Link(null,null);
        var links=jdbc.query("SELECT id AS product_id,NULL::uuid AS sku_id FROM commerce.product WHERE id=? AND deleted_at IS NULL UNION ALL SELECT p.id,s.id FROM commerce.sku s JOIN commerce.product p ON p.id=s.product_id WHERE s.id=? AND p.deleted_at IS NULL",(r,n)->new Link(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),resource,resource);
        if(links.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);
        var result=links.getFirst();if(lock){bound();var found=jdbc.query("SELECT id FROM commerce.product WHERE id=? AND deleted_at IS NULL FOR UPDATE",(r,n)->r.getObject(1,UUID.class),result.product());if(found.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);}
        return result;
    }
    private Instant instant(java.sql.Timestamp value){return value==null?null:value.toInstant();}
    public Stored find(UUID asset,boolean lock){
        if(lock){bound();var products=jdbc.query("SELECT product_id FROM commerce.asset_reference WHERE id=?",(r,n)->r.getObject(1,UUID.class),asset);if(products.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);if(products.getFirst()!=null)linked(products.getFirst(),true);}
        var rows=jdbc.query("SELECT a.*,t.owner_party_id,t.source_key,t.final_key,t.upload_expires_at,t.scan_command_id,t.source_version,t.source_etag,t.state AS transfer_state,t.copy_lease_until,t.scan_request_hash,t.scan_result,t.scanner_version FROM commerce.asset_reference a LEFT JOIN commerce.file_transfer t ON t.id=a.id WHERE a.id=?"+(lock?" FOR UPDATE OF a":""),(r,n)->new Stored(r.getObject("id",UUID.class),r.getObject("owner_party_id",UUID.class),r.getObject("product_id",UUID.class),r.getObject("sku_id",UUID.class),r.getObject("shipment_id",UUID.class),r.getString("object_key"),r.getString("purpose"),r.getString("mime_type"),r.getLong("byte_size"),r.getString("checksum_sha256"),r.getString("status"),r.getString("classification"),r.getLong("version"),instant(r.getTimestamp("created_at")),r.getString("source_key"),r.getString("final_key"),instant(r.getTimestamp("upload_expires_at")),r.getObject("scan_command_id",UUID.class),r.getString("source_version"),r.getString("source_etag"),r.getString("transfer_state"),instant(r.getTimestamp("copy_lease_until")),r.getString("scan_request_hash"),r.getString("scan_result"),r.getString("scanner_version")),asset);
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);if(lock)jdbc.query("SELECT id FROM commerce.file_transfer WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),asset);return rows.getFirst();
    }
    public void access(ActorAssertion.Actor actor,Stored asset,boolean lock){
        String role=asset.purpose().equals("SHIPPING_LABEL")?"OPERATIONS":"CONTENT_EDITOR";authorize(actor,role,lock);
        if(asset.purpose().equals("SHIPPING_LABEL")){if(!asset.classification().equals("PRIVATE")||asset.shipment()==null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);return;}
        if(!Set.of("IMAGE","BROCHURE").contains(asset.purpose())||asset.product()==null&& !actor.party().equals(asset.owner()))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        if(asset.product()!=null)linked(asset.product(),lock);
    }
    public Stored create(UUID id,ActorAssertion.Actor actor,FileValues.Upload input,String source,String target,Instant expiry){
        bound();authorize(actor,"CONTENT_EDITOR",true);var link=linked(input.linkedResourceId(),true);var now=java.sql.Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO commerce.asset_reference(id,created_by,updated_by,product_id,sku_id,object_key,purpose,mime_type,byte_size,checksum_sha256,status,classification,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,'UPLOADING','PUBLIC',?)",id,actor.party().toString(),actor.party().toString(),link.product(),link.sku(),source,input.purpose(),input.mimeType(),input.size(),input.checksumSha256(),now);
        jdbc.update("INSERT INTO commerce.file_transfer(id,created_by,updated_by,owner_party_id,source_key,final_key,upload_expires_at) VALUES (?,?,?,?,?,?,?)",id,actor.party().toString(),actor.party().toString(),actor.party(),source,target,java.sql.Timestamp.from(expiry));return find(id,false);
    }
    public Stored quarantined(Stored v,FileStorage.ObjectInfo verified,UUID command,String actor){
        bound();jdbc.update("UPDATE commerce.file_transfer SET source_version=?,source_etag=?,scan_command_id=?,state='QUARANTINED',version=version+1,updated_by=?,updated_at=now() WHERE id=? AND state='UPLOADING'",verified.version(),verified.etag(),command,actor,v.id());
        if(jdbc.update("UPDATE commerce.asset_reference SET status='QUARANTINED',version=version+1,updated_by=?,updated_at=now() WHERE id=? AND status='UPLOADING' AND version=?",actor,v.id(),v.version())!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);return find(v.id(),false);
    }
    public Stored claim(Stored v,FileValues.Scan scan,String hash,Instant lease,String actor){
        bound();if(jdbc.update("UPDATE commerce.file_transfer SET state='COPYING',scan_request_hash=?,scan_result=?,scanner_version=?,copy_lease_until=?,version=version+1,updated_by=?,updated_at=now() WHERE id=? AND state='QUARANTINED'",hash,scan.result(),scan.scannerVersion(),java.sql.Timestamp.from(lease),actor,v.id())!=1)throw new Problem.Failure(ProblemCode.CONFLICT);return find(v.id(),false);
    }
    public void unknown(UUID id,String hash,String actor){
        if(jdbc.update("UPDATE commerce.file_transfer SET state='UNKNOWN',copy_lease_until=NULL,version=version+1,updated_by=?,updated_at=now() WHERE id=? AND scan_request_hash=? AND state IN ('COPYING','UNKNOWN')",actor,id,hash)!=1)throw new Problem.Failure(ProblemCode.CONFLICT);
    }
    public Stored finish(Stored v,String state,String actor){
        bound();if(!Set.of("CLEAN","REJECTED").contains(state))throw ApiValues.invalid();
        if(jdbc.update("UPDATE commerce.asset_reference SET status=?,object_key=?,version=version+1,updated_by=?,updated_at=now() WHERE id=? AND status='QUARANTINED' AND version=?",state,state.equals("CLEAN")?v.finalKey():v.sourceKey(),actor,v.id(),v.version())!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
        jdbc.update("UPDATE commerce.file_transfer SET state=?,copy_lease_until=NULL,version=version+1,updated_by=?,updated_at=now() WHERE id=?",state,actor,v.id());return find(v.id(),false);
    }
}
