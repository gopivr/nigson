package com.nigson.commerce.inventory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
/** Current commerce OPERATIONS; administrative roles do not imply stock authority. */
public final class InventoryAuthority {
 private final JdbcTemplate jdbc;public InventoryAuthority(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void require(ActorAssertion.Actor actor){if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(actor.organization()!=null||!actor.roles().contains("OPERATIONS")||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role='OPERATIONS' AND r.revoked_at IS NULL)",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject())))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
 public void stabilize(ActorAssertion.Actor actor){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,java.util.UUID.class),actor.party());require(actor);}
}
