package com.nigson.commerce.catalogue;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.ActorAssertion;

/** Current owned editor/publisher roles; ADMIN is not an implicit content permission. */
public final class CatalogueAuthority {
    private final JdbcTemplate jdbc;
    public CatalogueAuthority(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void require(ActorAssertion.Actor actor,String role){
        if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);
        if(!Set.of("CONTENT_EDITOR","CONTENT_PUBLISHER").contains(role)||actor.organization()!=null||!actor.roles().contains(role)||!Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role=? AND r.revoked_at IS NULL)",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject(),role)))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
    }
    public void stabilize(ActorAssertion.Actor actor,String role){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");
        jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());require(actor,role);
    }
}
