package com.nigson.commerce.security;
import com.nigson.shared.security.ActorAssertion;
import java.util.UUID;
/** The signer never receives user/model-selected roles, subject or organization authority. */
public final class ActorDelegation {
 private final JdbcSessionTrust sessions;private final CommerceAuthority authority;private final IdentityKeys signer;
 public ActorDelegation(JdbcSessionTrust sessions,CommerceAuthority authority,IdentityKeys signer){this.sessions=sessions;this.authority=authority;this.signer=signer;}
 public String issue(String sessionId,ActorAssertion.Binding compiledOperation,UUID selectedOrganization){
  return issue(sessionId,compiledOperation,selectedOrganization,null);
 }
 public String issue(String sessionId,ActorAssertion.Binding compiledOperation,UUID selectedOrganization,String requiredEntitlement){
  var session=sessions.read(sessionId,true);var actor=authority.forDelegation(session,compiledOperation,selectedOrganization,requiredEntitlement);return signer.sign(actor,compiledOperation);
 }
}
