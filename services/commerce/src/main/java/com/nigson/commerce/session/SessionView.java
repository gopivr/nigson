package com.nigson.commerce.session;
import java.time.Instant;import java.util.*;
public record SessionView(boolean authenticated,UUID partyId,String displayName,List<String> roles,List<Membership> organizationMemberships,Instant expiresAt,String csrfToken) {
 public record Membership(UUID organizationId,String role){}
 public static SessionView anonymous(){return new SessionView(false,null,null,List.of(),List.of(),null,null);}
}
