package com.nigson.commerce.security;
import java.util.UUID;
/** Owner session persistence port. Implementations preserve one-use flow and CAS semantics. */
public interface SessionStore {
 record Login(String sessionId,PendingLogin flow){@Override public String toString(){return "Login[protected]";}}
 void requireReturnPath(String path);
 Login begin(String returnTo);
 Login begin(String returnTo,String remoteHash);
 PendingLogin consume(String sessionId,String state);
 String authenticate(String oldSession,SessionRecord record);
 SessionRecord read(String sessionId,boolean touch);
 boolean replace(String sessionId,SessionRecord expected,SessionRecord updated);
 void csrf(SessionRecord record,String supplied);
 void logout(String sessionId);
 int revokeSubject(UUID party);
}
