package com.nigson.commerce.market;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
public final class MarketAuthority {
 private final JdbcTemplate jdbc;private final Clock clock;public MarketAuthority(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 public void require(ActorAssertion.Actor actor,String role){if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(!Set.of("ADMIN","FINANCE","CONTENT_PUBLISHER").contains(role)||actor.organization()!=null||!actor.roles().contains(role)||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role=? AND r.revoked_at IS NULL)",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject(),role)))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(Set.of("ADMIN","FINANCE").contains(role))ActorAssertion.recentMfa(actor,clock);}
 public void stabilize(ActorAssertion.Actor actor,String role){if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());require(actor,role);}
}
