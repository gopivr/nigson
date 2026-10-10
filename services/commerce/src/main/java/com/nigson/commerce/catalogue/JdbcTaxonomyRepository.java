package com.nigson.commerce.catalogue;

import java.util.*;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;

public final class JdbcTaxonomyRepository implements TaxonomyRepository {
    private final JdbcTemplate jdbc;private final Clock clock;
    public JdbcTaxonomyRepository(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    private String table(Kind kind){return kind==Kind.BRAND?"commerce.brand":"commerce.category";}
    public void serialize(){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
        jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
        try{jdbc.queryForObject("SELECT pg_advisory_xact_lock(711938201125::bigint)",Object.class);}
        catch(org.springframework.dao.DataAccessException failure){if(failure.getMostSpecificCause() instanceof java.sql.SQLException sql&&"55P03".equals(sql.getSQLState()))throw new Problem.Failure(ProblemCode.CONFLICT);throw failure;}
    }
    public Record find(Kind kind,UUID id){
        String extra=kind==Kind.BRAND?"NULL::uuid AS parent_id,0 AS sort_order":"parent_id,sort_order";
        var values=jdbc.query("SELECT id,name,slug,"+extra+",active,version FROM "+table(kind)+" WHERE id=? AND deleted_at IS NULL FOR UPDATE",(r,n)->new Record(r.getObject("id",UUID.class),r.getString("name"),r.getString("slug"),r.getObject("parent_id",UUID.class),r.getInt("sort_order"),r.getBoolean("active"),r.getLong("version")),id);
        if(values.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);return values.getFirst();
    }
    public Record save(Kind kind,Record v,String actor,boolean create){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
        if(kind==Kind.CATEGORY)validateTree(v.id(),v.parentId());
        var now=java.sql.Timestamp.from(clock.instant());
        if(create){
            if(kind==Kind.BRAND)jdbc.update("INSERT INTO commerce.brand(id,created_by,updated_by,name,slug,active) VALUES (?,?,?,?,?,?)",v.id(),actor,actor,v.name(),v.slug(),v.active());
            else jdbc.update("INSERT INTO commerce.category(id,created_by,updated_by,name,slug,parent_id,sort_order,active) VALUES (?,?,?,?,?,?,?,?)",v.id(),actor,actor,v.name(),v.slug(),v.parentId(),v.sortOrder(),v.active());
        }else{
            int changed=kind==Kind.BRAND?jdbc.update("UPDATE commerce.brand SET name=?,slug=?,active=?,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?",v.name(),v.slug(),v.active(),now,actor,v.id(),v.version()):jdbc.update("UPDATE commerce.category SET name=?,slug=?,parent_id=?,sort_order=?,active=?,version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?",v.name(),v.slug(),v.parentId(),v.sortOrder(),v.active(),now,actor,v.id(),v.version());
            if(changed!=1)throw new Problem.Failure(ProblemCode.VERSION_MISMATCH);
        }
        return find(kind,v.id());
    }
    public void validateTree(UUID id,UUID parent){
        if(parent!=null){if(id.equals(parent))throw rule();
            var ancestor=jdbc.query("WITH RECURSIVE ancestors AS (SELECT id,parent_id,1 AS depth FROM commerce.category WHERE id=? AND deleted_at IS NULL UNION ALL SELECT c.id,c.parent_id,a.depth+1 FROM commerce.category c JOIN ancestors a ON c.id=a.parent_id WHERE a.depth<6 AND c.deleted_at IS NULL) SELECT id,depth FROM ancestors",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getInt(2)),parent);
            if(ancestor.isEmpty())throw new Problem.Failure(ProblemCode.NOT_FOUND);
            if(ancestor.stream().anyMatch(e->e.getKey().equals(id))||ancestor.stream().anyMatch(e->e.getValue()>=5))throw rule();
            int depth=ancestor.stream().mapToInt(Map.Entry::getValue).max().orElseThrow();
            Integer height=jdbc.queryForObject("WITH RECURSIVE descendants AS (SELECT id,1 AS depth FROM commerce.category WHERE id=? UNION ALL SELECT c.id,d.depth+1 FROM commerce.category c JOIN descendants d ON c.parent_id=d.id WHERE d.depth<6 AND c.deleted_at IS NULL) SELECT coalesce(max(depth),1) FROM descendants",Integer.class,id);
            if(depth+height>5)throw rule();
        }else{
            Integer height=jdbc.queryForObject("WITH RECURSIVE descendants AS (SELECT id,1 AS depth FROM commerce.category WHERE id=? UNION ALL SELECT c.id,d.depth+1 FROM commerce.category c JOIN descendants d ON c.parent_id=d.id WHERE d.depth<6 AND c.deleted_at IS NULL) SELECT coalesce(max(depth),1) FROM descendants",Integer.class,id);
            if(height>5)throw rule();
        }
    }
    private Problem.Failure rule(){return new Problem.Failure(ProblemCode.CATALOGUE_RULE_VIOLATION);}
}
