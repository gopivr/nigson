package com.nigson.integration.notification;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Integration-owned recipient/context encryption and keyed command identity. */
public final class StaffReplyCrypto {
    private final String revision;private final byte[] encryptionKey,requestKey;private final SecureRandom random=new SecureRandom();
    public StaffReplyCrypto(String revision,byte[] encryptionKey,byte[] requestKey){if(revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}")||encryptionKey==null||encryptionKey.length!=32||requestKey==null||requestKey.length!=32||MessageDigest.isEqual(encryptionKey,requestKey))throw new IllegalArgumentException("STAFF_REPLY_CRYPTO_POLICY_REQUIRED");this.revision=revision;this.encryptionKey=encryptionKey.clone();this.requestKey=requestKey.clone();}
    public byte[] encrypt(UUID job,String kind,byte[] clear){try{if(job==null||job.equals(new UUID(0,0))||!Set.of("RECIPIENT","VARIABLES").contains(kind)||clear==null||clear.length<1||clear.length>8000)throw invalid();byte[] nonce=new byte[12];random.nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(job,kind));byte[] encrypted=cipher.doFinal(clear);byte[] result=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,result,0,nonce.length);System.arraycopy(encrypted,0,result,nonce.length,encrypted.length);return result;}catch(Exception failure){throw invalid();}}
    public byte[] decrypt(UUID job,String kind,byte[] ciphertext){try{if(job==null||job.equals(new UUID(0,0))||!Set.of("RECIPIENT","VARIABLES").contains(kind)||ciphertext==null||ciphertext.length<29||ciphertext.length>8192)throw invalid();byte[] nonce=Arrays.copyOf(ciphertext,12),payload=Arrays.copyOfRange(ciphertext,12,ciphertext.length);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(job,kind));return cipher.doFinal(payload);}catch(Exception failure){throw invalid();}}
    public String commandHash(byte[] canonical){try{if(canonical==null||canonical.length<1||canonical.length>8192)throw invalid();var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(requestKey,"HmacSHA256"));mac.update("nigson-staff-reply-command-v1\n".getBytes(StandardCharsets.US_ASCII));return HexFormat.of().formatHex(mac.doFinal(canonical));}catch(Exception failure){throw invalid();}}
    public String requestHash(byte[] canonical){try{if(canonical==null||canonical.length<1||canonical.length>8192)throw invalid();var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(requestKey,"HmacSHA256"));mac.update("nigson-staff-reply-request-v1\n".getBytes(StandardCharsets.US_ASCII));return HexFormat.of().formatHex(mac.doFinal(canonical));}catch(Exception failure){throw invalid();}}
    public String revision(){return revision;}
    private byte[] aad(UUID job,String kind){return ("NIGSTAFFREPLY1:"+revision+":"+job+":"+kind).getBytes(StandardCharsets.US_ASCII);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("STAFF_REPLY_CONTEXT_INVALID");}
    @Override public String toString(){return "StaffReplyCrypto[protected]";}
}
