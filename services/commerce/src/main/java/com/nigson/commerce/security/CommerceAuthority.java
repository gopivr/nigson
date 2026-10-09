package com.nigson.commerce.security;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.security.ActorAssertion;

/** Current commerce authority. Cookie and Keycloak roles are never permission storage. */
public final class CommerceAuthority {
    private final JdbcTemplate jdbc;private final Clock clock;private final int phase;
    public CommerceAuthority(JdbcTemplate jdbc,Clock clock,int phase){if(phase<1||phase>5)throw new IllegalArgumentException("PHASE_REQUIRED");this.jdbc=jdbc;this.clock=clock;this.phase=phase;}
    public ActorAssertion.Actor resolve(SessionRecord session,UUID organization,Set<String> scopes) {return resolve(session,organization,null,scopes);}
    public ActorAssertion.Actor resolve(SessionRecord session,UUID organization,String requiredEntitlement,Set<String> scopes){return current(session.party(),session.issuer(),session.subject(),session.mfaAt(),organization,requiredEntitlement,scopes);}
    public ActorAssertion.Actor delegated(com.nigson.shared.security.PrivateWorkload.Proof proof,UUID organization){return delegated(proof,organization,null);}
    public ActorAssertion.Actor delegated(com.nigson.shared.security.PrivateWorkload.Proof proof,UUID organization,String requiredEntitlement){
        var claimed=proof.actor();if(claimed==null||!Objects.equals(claimed.organization(),organization))throw ActorAssertion.denied();
        var current=current(claimed.party(),claimed.identityIssuer(),claimed.identitySubject(),claimed.mfaAt(),organization,requiredEntitlement,claimed.scopes());var roles=new HashSet<>(current.roles());roles.retainAll(claimed.roles());return new ActorAssertion.Actor(current.party(),current.identityIssuer(),current.identitySubject(),organization,roles,current.scopes(),current.mfaAt());
    }
    public void requireOwner(ActorAssertion.Actor actor,UUID resourceParty){if(resourceParty==null||!actor.party().equals(resourceParty))throw new com.nigson.shared.problem.Problem.Failure(com.nigson.shared.problem.ProblemCode.NOT_FOUND);}
    public void requireRole(ActorAssertion.Actor actor,String role){if(!Set.of("ADMIN","CONTENT_EDITOR","CONTENT_PUBLISHER","SALES","OPERATIONS","FINANCE","ANALYST").contains(role)||!actor.roles().contains(role))throw ActorAssertion.denied();}
    private ActorAssertion.Actor current(UUID partyId,String identityIssuer,String identitySubject,Instant mfaAt,UUID organization,String requiredEntitlement,Set<String> scopes) {
        var party=jdbc.query("SELECT id FROM commerce.party WHERE id=? AND identity_issuer=? AND identity_subject=? AND status='ACTIVE'",(r,n)->r.getObject(1,UUID.class),partyId,identityIssuer,identitySubject);if(party.size()!=1)throw ActorAssertion.denied();
        var roles=new HashSet<>(jdbc.query("SELECT role FROM commerce.staff_role_assignment WHERE party_id=? AND revoked_at IS NULL ORDER BY role LIMIT 8",(r,n)->r.getString(1),partyId));if(roles.size()>7)throw ActorAssertion.denied();roles.add("CUSTOMER");
        if(organization!=null){if(requiredEntitlement==null||!requiredEntitlement.matches("[A-Z][A-Z0-9_]{1,39}")||phase<3||organization.equals(new UUID(0,0)))throw ActorAssertion.denied();Integer count=jdbc.queryForObject("SELECT count(*) FROM commerce.organization_membership m JOIN commerce.organization o ON o.id=m.organization_id JOIN commerce.account_entitlement e ON e.organization_id=m.organization_id WHERE m.party_id=? AND m.organization_id=? AND m.status='ACTIVE' AND o.status='ACTIVE' AND e.status='ACTIVE' AND e.entitlement=?",Integer.class,partyId,organization,requiredEntitlement);if(count==null||count<1)throw ActorAssertion.denied();}
        return new ActorAssertion.Actor(partyId,identityIssuer,identitySubject,organization,roles,scopes,mfaAt);
    }
    public ActorAssertion.Actor forDelegation(SessionRecord session,ActorAssertion.Binding binding,UUID organization) {return forDelegation(session,binding,organization,null);}
    public ActorAssertion.Actor forDelegation(SessionRecord session,ActorAssertion.Binding binding,UUID organization,String requiredEntitlement) {
        var route=com.nigson.shared.security.PrivateWorkload.ROUTES.stream().filter(r->r.owner().equals(binding.audience())&&r.method().equals(binding.method())&&r.scope().equals(binding.operation())&&r.phase()<=phase&&r.scope().contains("_WITH_SIGNED_")).findFirst().orElseThrow(ActorAssertion::denied);
        // Match resource against the compiled target registry, independently of the caller's body.
        var matcher=new com.nigson.shared.security.PrivateWorkload(binding.audience(),phase,new com.nigson.shared.security.PrivateIngress(List.of("127.0.0.1/32")),r->token->{throw ActorAssertion.denied();},Map.of(),Set.of(session.issuer()),clock);
        if(!matcher.match(binding.method(),binding.resource()).api().equals(route.api()))throw ActorAssertion.denied();
        var actor=resolve(session,organization,requiredEntitlement,Set.of(route.scope()));
        if(route.scope().endsWith("_WITH_SIGNED_ADMIN")){if(!actor.roles().contains("ADMIN"))throw ActorAssertion.denied();ActorAssertion.recentMfa(actor,clock);}
        if(route.scope().endsWith("_WITH_SIGNED_STAFF")){if(!actor.roles().contains("OPERATIONS"))throw ActorAssertion.denied();ActorAssertion.recentMfa(actor,clock);}
        return actor;
    }
}
