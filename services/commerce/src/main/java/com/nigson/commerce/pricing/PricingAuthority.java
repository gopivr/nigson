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

 /** User-approved ADMIN exception applies only to price-book management. */
 public void requirePriceBook(ActorAssertion.Actor actor,String fallbackRole){
  if(actor==null||!actor.roles().contains("ADMIN")){require(actor,fallbackRole);return;}
  if(actor.organization()!=null||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.party p JOIN commerce.staff_role_assignment r ON r.party_id=p.id WHERE p.id=? AND p.identity_issuer=? AND p.identity_subject=? AND p.status='ACTIVE' AND r.role=? AND r.revoked_at IS NULL)",Boolean.class,actor.party(),actor.identityIssuer(),actor.identitySubject(),"ADMIN")))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
 }
 public void stabilizePriceBook(ActorAssertion.Actor actor,String fallbackRole){jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());requirePriceBook(actor,fallbackRole);}
 public void stabilize(ActorAssertion.Actor actor,String role){jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());require(actor,role);}
 public void organization(ActorAssertion.Actor actor,UUID organization,boolean lock){require(actor,"CUSTOMER");if(phase<3||entitlement.isEmpty())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var rows=jdbc.query("SELECT o.id FROM commerce.organization o JOIN commerce.organization_membership m ON m.organization_id=o.id JOIN commerce.account_entitlement e ON e.organization_id=o.id WHERE o.id=? AND o.status='ACTIVE' AND m.party_id=? AND m.status='ACTIVE' AND e.status='ACTIVE' AND e.entitlement=?"+(lock?" FOR SHARE OF o,m,e":""),(r,n)->r.getObject(1,UUID.class),organization,actor.party(),entitlement);if(rows.isEmpty())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
 public void serviceOrganization(UUID party,UUID organization){if(phase<3||entitlement.isEmpty()||party==null||organization==null)throw new Problem.Failure(ProblemCode.ACCESS_DENIED);var rows=jdbc.query("SELECT o.id FROM commerce.organization o JOIN commerce.organization_membership m ON m.organization_id=o.id JOIN commerce.account_entitlement e ON e.organization_id=o.id JOIN commerce.party p ON p.id=m.party_id WHERE o.id=? AND o.status='ACTIVE' AND m.party_id=? AND m.status='ACTIVE' AND e.status='ACTIVE' AND e.entitlement=? AND p.status='ACTIVE'",(r,n)->r.getObject(1,UUID.class),organization,party,entitlement);if(rows.isEmpty())throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
 /** Canonical partner context includes suspended claims: never silently downgrade to retail. */
 public UUID automaticOrganization(ActorAssertion.Actor actor,boolean lock){
  if(actor==null)return null;require(actor,"CUSTOMER");if(phase<3)return null;if(entitlement.isEmpty()){if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM commerce.organization_membership m JOIN commerce.account_entitlement e ON e.organization_id=m.organization_id WHERE m.party_id=?)",Boolean.class,actor.party())))throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);return null;}
  if(lock)jdbc.query("SELECT id FROM commerce.party WHERE id=? FOR SHARE",(r,n)->r.getObject(1,UUID.class),actor.party());
  var rows=jdbc.query("SELECT o.id,o.status,m.status,e.status,e.partner_type FROM commerce.organization o JOIN commerce.organization_membership m ON m.organization_id=o.id JOIN commerce.account_entitlement e ON e.organization_id=o.id WHERE m.party_id=? AND e.entitlement=? ORDER BY o.id LIMIT 3"+(lock?" FOR SHARE OF o,m,e":""),(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5)},actor.party(),entitlement);
  if(rows.isEmpty())return null;
  if(rows.size()!=1||!"ACTIVE".equals(rows.getFirst()[1])||!"ACTIVE".equals(rows.getFirst()[2])||!"ACTIVE".equals(rows.getFirst()[3])||!Set.of("WHOLESALE","DISTRIBUTOR").contains(rows.getFirst()[4]))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
  return (UUID)rows.getFirst()[0];
 }
 public void currentContext(ActorAssertion.Actor actor,UUID organization,boolean lock){if(!Objects.equals(automaticOrganization(actor,lock),organization))throw new Problem.Failure(ProblemCode.CHECKOUT_REPRICE_REQUIRED);}
 public int phase(){return phase;}
 public String entitlement(){return entitlement;}
}
