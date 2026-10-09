package com.nigson.commerce.security;
import java.time.Instant;
/** Typed one-use login intent; no arbitrary framework object serialization. */
public record PendingLogin(int version,String state,String nonce,String verifier,String returnTo,Instant expiresAt) {
    public PendingLogin {if(version!=1||state==null||!state.matches("[A-Za-z0-9_-]{43}")||nonce==null||!nonce.matches("[A-Za-z0-9_-]{43}")||verifier==null||!verifier.matches("[A-Za-z0-9_-]{43}")||returnTo==null||!returnTo.matches("/[A-Za-z0-9/_-]{0,200}")||expiresAt==null)throw new IllegalArgumentException("LOGIN_RECORD_REJECTED");}
    @Override public String toString(){return "PendingLogin[protected]";}
}
