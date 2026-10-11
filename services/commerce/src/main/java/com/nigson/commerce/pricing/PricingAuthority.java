package com.nigson.commerce.pricing;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
/** Recheck persisted commerce authority before replay and stabilize it inside commands. */
public final class PricingAuthority {
 private final JdbcTemplate jdbc;private final Clock clock;private final int phase;private final String entitlement;
 public PricingAuthority(JdbcTemplate jdbc,Clock clock,int phase,String entitlement){if(phase<1||phase>5||entitlement==null||!entitlement.isEmpty()&&!entitlement.matches("[A-Z][A-Z0-9_]{1,39}"))throw new IllegalArgumentException("PRICING_AUTHORITY_CONFIGURATION_INVALID");this.jdbc=jdbc;this.clock=clock;this.phase=phase;this.entitlement=entitlement;}
 public void require(ActorAssertion.Actor actor,String role){if(actor==null)throw new Problem.Failure(ProblemCode.AUTH_REQUIRED);if(actor.organization()!=null||!Set.of("SALES","FINANCE","CUSTOMER").contains(role)||!actor.roles().contains(role)||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND (?='CUSTOMER' OR EXISTS(SELECT 1 FROM commerce.staff_role_assignment r WHERE r.party_id=p.id AND r.role=? AND r.revoked_at IS NULL)))",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject(),role,role)))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);if(role.equals("FINANCE"))ActorAssertion.recentMfa(actor,clock);}
 public void stabilize(ActorAssertion.Actor actor,String role){jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());require(actor,role);}
 public void organization(ActorAssertion.Actor actor,UUID organization,boolean lock){require(actor,"CUSTOMER");if(phase<3||entitlement.isEmpty())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var rows=jdbc.query("SELECT o.id FROM commerce.organization o JOIN commerce.organization_membership m ON m.organization_id=o.id JOIN commerce.account_entitlement e ON e.organization_id=o.id WHERE o.id=? AND o.status='ACTIVE' AND m.party_id=? AND m.status='ACTIVE' AND e.status='ACTIVE' AND e.entitlement=?"+(lock?" FOR SHARE OF o,m,e":""),(r,n)->r.getObject(1,UUID.class),organization,actor.party(),entitlement);if(rows.isEmpty())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
 public int phase(){return phase;}
}
