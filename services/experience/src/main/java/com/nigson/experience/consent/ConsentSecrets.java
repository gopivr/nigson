package com.nigson.experience.consent;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Purpose-separated email, capability and confirmation-context protection for consent records. */
public final class ConsentSecrets {
 private final int hashVersion;private final byte[] hashKey;private final String active;private final Map<String,byte[]> cipherKeys;private final SecureRandom random=new SecureRandom();
 public ConsentSecrets(int hashVersion,byte[] hashKey,String active,Map<String,byte[]> cipherKeys){if(hashVersion<1||hashKey==null||hashKey.length!=32||active==null||!active.matches("[A-Za-z0-9_-]{1,40}")||cipherKeys==null||cipherKeys.isEmpty()||cipherKeys.size()>3||!cipherKeys.containsKey(active))throw new IllegalArgumentException("CONSENT_KEY_POLICY_REQUIRED");var copy=new HashMap<String,byte[]>();cipherKeys.forEach((id,key)->{if(id==null||!id.matches("[A-Za-z0-9_-]{1,40}")||key==null||key.length!=32||MessageDigest.isEqual(hashKey,key))throw new IllegalArgumentException("CONSENT_KEY_POLICY_REQUIRED");copy.put(id,key.clone());});this.hashVersion=hashVersion;this.hashKey=hashKey.clone();this.active=active;this.cipherKeys=Map.copyOf(copy);}
 public int hashVersion(){return hashVersion;}
 public String emailHash(String email){return hmac("EMAIL:"+hashVersion+":"+email.strip().toLowerCase(Locale.ROOT));}
 public String capabilityHash(String purpose,String token){if(!Set.of("NEWSLETTER_CONFIRM","NEWSLETTER_UNSUBSCRIBE").contains(purpose)||token==null||token.isBlank()||token.length()>512)throw rejected();return hmac(purpose+":"+token);}
 private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(hashKey,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException failure){throw rejected();}}
 public String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 public byte[] encrypt(UUID id,String purpose,String value){try{if(id==null||id.equals(new UUID(0,0))||!Set.of("EMAIL","CONFIRMATION_CONTEXT").contains(purpose)||value==null||value.length()>4096)throw rejected();byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(cipherKeys.get(active),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(id,purpose,active));byte[] body=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));return ("NIGCONSENT1."+active+"."+purpose+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(body)).getBytes(StandardCharsets.US_ASCII);}catch(Exception failure){throw rejected();}}
 public String decrypt(UUID id,String purpose,byte[] stored){try{if(stored==null||stored.length>8192)throw rejected();String[] parts=new String(stored,StandardCharsets.US_ASCII).split("\\.",-1);if(parts.length!=5||!parts[0].equals("NIGCONSENT1")||!parts[2].equals(purpose)||!cipherKeys.containsKey(parts[1]))throw rejected();byte[] nonce=Base64.getUrlDecoder().decode(parts[3]);if(nonce.length!=12)throw rejected();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(cipherKeys.get(parts[1]),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(id,purpose,parts[1]));return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[4])),StandardCharsets.UTF_8);}catch(Exception failure){throw rejected();}}
 private static byte[] aad(UUID id,String purpose,String revision){return ("NIGCONSENT1:"+id+":"+purpose+":"+revision).getBytes(StandardCharsets.US_ASCII);}
 private static IllegalArgumentException rejected(){return new IllegalArgumentException("CONSENT_SECRET_REJECTED");}
 @Override public String toString(){return "ConsentSecrets[protected]";}
}
