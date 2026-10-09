package com.nigson.commerce.security;

import java.time.*;
import java.util.*;
/** Sole persistent BFF identity record. Authorization is resolved from commerce on every use. */
public record SessionRecord(int version,UUID party,String issuer,String subject,Instant loginAt,Instant mfaAt,
                            String accessToken,String refreshToken,Instant accessExpiresAt,String csrfToken,TokenState tokenState) {
    public enum TokenState { READY, REFRESHING }
    public SessionRecord {
        if(tokenState==null||version!=1||party==null||party.equals(new UUID(0,0))||issuer==null||issuer.length()>500||subject==null||subject.isBlank()||subject.length()>200||loginAt==null||accessExpiresAt==null||csrfToken==null||!csrfToken.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("SESSION_RECORD_REJECTED");
        if(accessToken==null||accessToken.length()>16384||refreshToken==null||refreshToken.length()>16384||accessToken.isBlank()||refreshToken.isBlank()||mfaAt!=null&&mfaAt.isAfter(loginAt.plusSeconds(5)))throw new IllegalArgumentException("SESSION_RECORD_REJECTED");
    }
    public SessionRecord withState(TokenState state){return new SessionRecord(version,party,issuer,subject,loginAt,mfaAt,accessToken,refreshToken,accessExpiresAt,csrfToken,state);}
    public Instant absoluteExpiry(){return loginAt.plusSeconds(43200);}
    @Override public String toString(){return "SessionRecord[protected]";}
}
