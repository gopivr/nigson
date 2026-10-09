package com.nigson.commerce.session;
import com.nigson.commerce.security.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;import java.util.*;
/** Provider HTTP occurs before the bounded owner snapshot transaction. */
public final class SessionService {
 private final SessionStore sessions;private final SessionLifecycle lifecycle;private final CommerceAuthority authority;private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Clock clock;private final int phase;
 public SessionService(SessionStore sessions,SessionLifecycle lifecycle,CommerceAuthority authority,JdbcTemplate jdbc,TransactionTemplate tx,Clock clock,int phase){this.sessions=sessions;this.lifecycle=lifecycle;this.authority=authority;this.jdbc=jdbc;this.tx=tx;this.clock=clock;this.phase=phase;}
 public SessionView view(String id){
  if(id==null)return SessionView.anonymous();SessionRecord record;
  try{record=sessions.read(id,true);}catch(IllegalArgumentException|IllegalStateException|org.springframework.security.access.AccessDeniedException expired){return SessionView.anonymous();}
  if(!clock.instant().isBefore(record.accessExpiresAt().minusSeconds(30))){try{record=lifecycle.refresh(id);}catch(RuntimeException failed){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}}
  final SessionRecord current=record;
  try{return tx.execute(status->{var actor=authority.resolve(current,null,Set.of());var memberships=phase<3?List.<SessionView.Membership>of():jdbc.query("SELECT m.organization_id,m.role FROM commerce.organization_membership m JOIN commerce.organization o ON o.id=m.organization_id WHERE m.party_id=? AND m.status='ACTIVE' AND o.status='ACTIVE' ORDER BY m.organization_id LIMIT 101",(r,n)->new SessionView.Membership(r.getObject(1,UUID.class),r.getString(2)),actor.party());if(memberships.size()>100)throw ActorAssertion.denied();String name=jdbc.queryForObject("SELECT display_name FROM commerce.party WHERE id=?",String.class,actor.party());var deadlines=jdbc.query("SELECT expiry_time FROM commerce.spring_session WHERE session_id=? AND expiry_time>?",(r,n)->r.getLong(1),id,clock.instant().toEpochMilli());if(deadlines.isEmpty())return SessionView.anonymous();long expiry=deadlines.getFirst();return new SessionView(true,actor.party(),name,actor.roles().stream().sorted().toList(),List.copyOf(memberships),(Instant.ofEpochMilli(expiry).isBefore(current.absoluteExpiry())?Instant.ofEpochMilli(expiry):current.absoluteExpiry()),current.csrfToken());});}
  catch(org.springframework.security.access.AccessDeniedException denied){sessions.logout(id);return SessionView.anonymous();}
 }
 public void logout(String id,String csrf){try{lifecycle.logout(id,csrf);}catch(IllegalStateException failed){if("OIDC_TRUST_REJECTED".equals(failed.getMessage()))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);throw failed;}}
}
