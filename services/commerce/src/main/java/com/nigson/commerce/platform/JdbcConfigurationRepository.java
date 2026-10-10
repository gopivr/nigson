package com.nigson.commerce.platform;

import java.util.*;
import java.time.Clock;
import tools.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ActorAssertion;

public final class JdbcConfigurationRepository implements ConfigurationRepository {
    private final JdbcTemplate jdbc;private final Clock clock;
    public JdbcConfigurationRepository(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public void requireAdministrator(ActorAssertion.Actor actor){
        if(actor.organization()!=null||!actor.roles().contains("ADMIN")||!Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role='ADMIN' AND r.revoked_at IS NULL)",
            Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject())))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
    private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");}
    public void serialize(String key,UUID actor){
        transaction();jdbc.execute("SET LOCAL lock_timeout='500ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
        try{jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"configuration:"+key);
            jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor);
        }catch(org.springframework.dao.DataAccessException failure){
            if(failure.getMostSpecificCause() instanceof java.sql.SQLException sql&&Set.of("55P03","57014").contains(sql.getSQLState()))throw new Problem.Failure(ProblemCode.CONFLICT);
            throw failure;
        }
    }
    public Snapshot latest(String key){
        var rows=jdbc.query("SELECT id,revision,version,active FROM commerce.feature_configuration WHERE key=? ORDER BY revision DESC LIMIT 1",(r,n)->new Snapshot(r.getObject(1,UUID.class),r.getLong(2),r.getLong(3),r.getBoolean(4)),key);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public UUID append(String key,long revision,long version,JsonNode value,boolean active,ActorAssertion.Actor actor){
        transaction();String subject=actor.party().toString();UUID id=EventCodec.uuid7(clock.instant());
        jdbc.update("INSERT INTO commerce.feature_configuration(id,created_by,updated_by,key,revision,version,value,active,approved_by) VALUES (?,?,?,?,?,?,?::jsonb,?,?)",id,subject,subject,key,revision,version,EventCodec.JSON.writeValueAsString(value),active,active?subject:null);
        return jdbc.queryForObject("SELECT id FROM commerce.feature_configuration WHERE key=? ORDER BY revision LIMIT 1",UUID.class,key);
    }
}
