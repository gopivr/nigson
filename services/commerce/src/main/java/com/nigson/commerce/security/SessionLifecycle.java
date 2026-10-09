package com.nigson.commerce.security;

import java.time.Clock;
import java.util.UUID;
import com.nigson.shared.security.ActorAssertion;
import io.micrometer.core.instrument.MeterRegistry;

/** Reusable security use cases. Public session DTO/provisioning/controllers belong to COM-SESSI-001. */
public final class SessionLifecycle {
    public interface PartyMapping { UUID mappedActiveParty(OidcTrust.Tokens identity); }
    private final SessionStore sessions;private final OidcTrust oidc;private final PartyMapping mapping;private final Clock clock;private final MeterRegistry metrics;
    public SessionLifecycle(SessionStore sessions,OidcTrust oidc,PartyMapping mapping,Clock clock,MeterRegistry metrics){this.sessions=sessions;this.oidc=oidc;this.mapping=mapping;this.clock=clock;this.metrics=metrics;}
    public record Callback(String sessionId,String returnTo) { @Override public String toString(){return "Callback[protected]";} }
    public String callback(String id,String state,String code) {return completeCallback(id,state,code).sessionId();}
    public Callback completeCallback(String id,String state,String code) {
        PendingLogin flow=sessions.consume(id,state);
        try {var token=oidc.exchange(code,flow);UUID party=mapping.mappedActiveParty(token);
            var record=new SessionRecord(1,party,token.issuer(),token.subject(),clock.instant(),token.mfaAt(),token.access(),token.refresh(),token.expires(),JdbcSessionTrust.opaque(),SessionRecord.TokenState.READY);
            String rotated=sessions.authenticate(id,record);metrics.counter("nigson.security.login").increment();return new Callback(rotated,flow.returnTo());
        }catch(RuntimeException failed){sessions.logout(id);metrics.counter("nigson.security.denied").increment();throw ActorAssertion.denied();}
    }
    public SessionRecord refresh(String id) {
        var previous=sessions.read(id,false);var claimed=previous.withState(SessionRecord.TokenState.REFRESHING);
        if(!sessions.replace(id,previous,claimed))throw ActorAssertion.denied();
        try {var token=oidc.refresh(previous);var updated=new SessionRecord(1,previous.party(),previous.issuer(),previous.subject(),previous.loginAt(),previous.mfaAt(),token.access(),token.refresh(),token.expires(),previous.csrfToken(),SessionRecord.TokenState.READY);
            if(!sessions.replace(id,claimed,updated))throw ActorAssertion.denied();return updated;
        }catch(RuntimeException failed){sessions.logout(id);metrics.counter("nigson.security.denied").increment();throw ActorAssertion.denied();}
    }
    public void logout(String id,String csrf) {
        var record=sessions.read(id,false);sessions.csrf(record,csrf);sessions.logout(id);metrics.counter("nigson.security.logout").increment();oidc.revoke(record.refreshToken());
    }
}
