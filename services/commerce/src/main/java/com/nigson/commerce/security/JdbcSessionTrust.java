package com.nigson.commerce.security;

import java.security.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.shared.security.ActorAssertion;

/** Spring Session JDBC physical schema, with atomic one-use login and fixation-safe rotation. */
public final class JdbcSessionTrust implements SessionStore {
    private record Row(String primary,byte[] bytes,long expiry) {}
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final SessionCodec codec;private final Clock clock;private final Set<String> returnPaths;private final com.nigson.commerce.platform.AuditWriter audit;
    private static final SecureRandom RANDOM=new SecureRandom();
    public JdbcSessionTrust(JdbcTemplate jdbc,TransactionTemplate tx,SessionCodec codec,Clock clock,Set<String> returnPaths) {
        this(jdbc,tx,codec,clock,returnPaths,null);
    }
    public JdbcSessionTrust(JdbcTemplate jdbc,TransactionTemplate tx,SessionCodec codec,Clock clock,Set<String> returnPaths,com.nigson.commerce.platform.AuditWriter audit) {
        this.audit=audit;
        if(returnPaths.isEmpty()||returnPaths.size()>32||returnPaths.stream().anyMatch(p->!p.matches("/[A-Za-z0-9/_-]{0,200}")||p.contains("//")))throw new IllegalArgumentException("BFF_RETURN_PATHS_REQUIRED");
        this.jdbc=jdbc;this.tx=tx;this.codec=codec;this.clock=clock;this.returnPaths=Set.copyOf(returnPaths);
    }
    public void requireReturnPath(String returnTo){if(!returnPaths.contains(returnTo))throw ActorAssertion.denied();}
    public Login begin(String returnTo) {return begin(returnTo,null);}
    public Login begin(String returnTo,String remoteHash) {
        requireReturnPath(returnTo);Instant now=clock.instant();var flow=new PendingLogin(1,opaque(),opaque(),opaque(),returnTo,now.plusSeconds(300));
        String id=UUID.randomUUID().toString(),primary=UUID.randomUUID().toString();byte[] encoded=codec.encode(flow);
        tx.executeWithoutResult(s->{
            if(remoteHash!=null){if(!remoteHash.matches("[a-f0-9]{64}"))throw com.nigson.shared.problem.ApiValues.invalid();jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"bff-login-budget");
                Integer own=jdbc.queryForObject("SELECT count(*) FROM commerce.spring_session WHERE principal_name=? AND expiry_time>?",Integer.class,"pending:"+remoteHash,now.toEpochMilli());
                Integer all=jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM commerce.spring_session WHERE principal_name LIKE 'pending:%' AND expiry_time>? LIMIT 10000) b",Integer.class,now.toEpochMilli());
                if(own>=20||all>=10000)throw new com.nigson.shared.problem.Problem.Failure(com.nigson.shared.problem.ProblemCode.RATE_LIMITED);}
            insert(primary,id,now,remoteHash==null?null:"pending:"+remoteHash,now.plusSeconds(300));attribute(primary,"nigson.login",encoded);});return new Login(id,flow);
    }
    /** Consume state before provider HTTP. A failed/uncertain code exchange requires a new login. */
    public PendingLogin consume(String sessionId,String state) {
        Row row=row(sessionId,"nigson.login");if(row==null)throw ActorAssertion.denied();var flow=(PendingLogin)codec.decode(row.bytes());
        if(state==null||!MessageDigest.isEqual(flow.state().getBytes(StandardCharsets.US_ASCII),state.getBytes(StandardCharsets.US_ASCII))||!clock.instant().isBefore(flow.expiresAt()))throw ActorAssertion.denied();
        int changed=jdbc.update("DELETE FROM commerce.spring_session_attributes WHERE session_primary_id=? AND attribute_name='nigson.login' AND attribute_bytes=?",row.primary(),row.bytes());
        if(changed!=1)throw ActorAssertion.denied();return flow;
    }
    /** Successful login rotates the externally visible ID and primary key, deleting all old attributes. */
    public String authenticate(String oldSession,SessionRecord record) {
        byte[] encoded=codec.encode(record);String id=UUID.randomUUID().toString(),primary=UUID.randomUUID().toString();Instant now=clock.instant();
        if(record.loginAt().isAfter(now.plusSeconds(5))||!now.isBefore(record.absoluteExpiry()))throw ActorAssertion.denied();
        tx.executeWithoutResult(s->{int changed=jdbc.update("DELETE FROM commerce.spring_session WHERE session_id=? AND expiry_time>? AND NOT EXISTS (SELECT 1 FROM commerce.spring_session_attributes a WHERE a.session_primary_id=commerce.spring_session.primary_id )",checked(oldSession),now.toEpochMilli());if(changed!=1)throw ActorAssertion.denied();insert(primary,id,now,record.party().toString(),earlier(now.plusSeconds(1800),record.absoluteExpiry()));attribute(primary,"nigson.identity",encoded);evidence(record.party(),com.nigson.shared.problem.AuditValues.Action.SESSION_LOGIN,id);});return id;
    }
    public SessionRecord read(String sessionId,boolean touch) {
        Row row=row(sessionId,"nigson.identity");if(row==null)throw ActorAssertion.denied();SessionRecord record=(SessionRecord)codec.decode(row.bytes());Instant now=clock.instant();
        if(record.tokenState()!=SessionRecord.TokenState.READY)throw ActorAssertion.denied();
        if(!now.isBefore(record.absoluteExpiry())){logout(sessionId);throw ActorAssertion.denied();}
        if(touch&&jdbc.update("UPDATE commerce.spring_session SET last_access_time=?,expiry_time=? WHERE primary_id=? AND expiry_time>?",now.toEpochMilli(),earlier(now.plusSeconds(1800),record.absoluteExpiry()).toEpochMilli(),row.primary(),now.toEpochMilli())!=1)throw ActorAssertion.denied();return record;
    }
    /** Optimistic token replacement: never resurrect a session deleted by logout/revocation. */
    public boolean replace(String sessionId,SessionRecord expected,SessionRecord updated) {
        if(!expected.party().equals(updated.party())||!expected.issuer().equals(updated.issuer())||!expected.subject().equals(updated.subject())||!expected.loginAt().equals(updated.loginAt())||!Objects.equals(expected.mfaAt(),updated.mfaAt())||!expected.csrfToken().equals(updated.csrfToken()))throw ActorAssertion.denied();
        Row row=row(sessionId,"nigson.identity");if(row==null||!expected.equals(codec.decode(row.bytes())))return false;byte[] bytes=codec.encode(updated);
        return jdbc.update("UPDATE commerce.spring_session_attributes a SET attribute_bytes=? FROM commerce.spring_session s WHERE a.session_primary_id=s.primary_id AND s.session_id=? AND a.attribute_name='nigson.identity' AND a.attribute_bytes=? AND s.expiry_time>?",bytes,checked(sessionId),row.bytes(),clock.instant().toEpochMilli())==1;
    }
    public void csrf(SessionRecord session,String supplied){if(supplied==null||!MessageDigest.isEqual(session.csrfToken().getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII)))throw ActorAssertion.denied();}
    public void logout(String sessionId){tx.executeWithoutResult(status->{var parties=jdbc.query("DELETE FROM commerce.spring_session WHERE session_id=? RETURNING principal_name",(r,n)->r.getString(1),checked(sessionId));for(String party:parties)if(party!=null&&!party.startsWith("pending:"))evidence(UUID.fromString(party),com.nigson.shared.problem.AuditValues.Action.SESSION_LOGOUT,sessionId);});}
    private void evidence(UUID party,com.nigson.shared.problem.AuditValues.Action action,String id){if(audit==null)return;var ctx=com.nigson.shared.observability.WireContext.current();audit.append(party.toString(),com.nigson.shared.problem.AuditValues.ActorKind.USER,action,com.nigson.shared.problem.AuditValues.Resource.SESSION,UUID.fromString(id),ctx==null?com.nigson.shared.events.EventCodec.uuid7(clock.instant()):ctx.correlationId,com.nigson.shared.problem.AuditValues.Reason.SECURITY_POLICY,null,Map.of("version",1),null);}
    public int revokeSubject(UUID party){if(party==null||party.equals(new UUID(0,0)))throw ActorAssertion.denied();return jdbc.update("DELETE FROM commerce.spring_session WHERE principal_name=?",party.toString());}
    private Row row(String id,String name){var rows=jdbc.query("SELECT s.primary_id,a.attribute_bytes,s.expiry_time FROM commerce.spring_session s JOIN commerce.spring_session_attributes a ON a.session_primary_id=s.primary_id WHERE s.session_id=? AND a.attribute_name=? AND s.expiry_time>?",(r,n)->new Row(r.getString(1),r.getBytes(2),r.getLong(3)),checked(id),name,clock.instant().toEpochMilli());return rows.isEmpty()?null:rows.getFirst();}
    private void insert(String primary,String id,Instant now,String party,Instant expiry){jdbc.update("INSERT INTO commerce.spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES (?,?,?,?,?,?,?)",primary,id,now.toEpochMilli(),now.toEpochMilli(),1800,expiry.toEpochMilli(),party);}
    private void attribute(String primary,String name,byte[] bytes){jdbc.update("INSERT INTO commerce.spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) VALUES (?,?,?)",primary,name,bytes);}
    private static String checked(String id){try{if(id==null||!UUID.fromString(id).toString().equals(id))throw ActorAssertion.denied();return id;}catch(Exception e){throw ActorAssertion.denied();}}
    public static String opaque(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static Instant earlier(Instant a,Instant b){return a.isBefore(b)?a:b;}
}
