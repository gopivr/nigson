package com.nigson.experience.consent;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Experience-owned key material; no code or phone is a logging/serialization value. */
public final class PhoneSecrets {
 private final int hashVersion; private final byte[] hashKey; private final String active;
 private final Map<String,byte[]> keys; private final SecureRandom random=new SecureRandom();
 public PhoneSecrets(int hashVersion,byte[] hashKey,String active,Map<String,byte[]> keys){
  if(hashVersion<1||hashKey==null||hashKey.length!=32||active==null||!active.matches("[A-Za-z0-9_-]{1,40}")||keys==null||keys.isEmpty()||keys.size()>3||!keys.containsKey(active))throw new IllegalArgumentException("PHONE_KEY_POLICY_REQUIRED");
  var copy=new HashMap<String,byte[]>();keys.forEach((name,key)->{if(name==null||!name.matches("[A-Za-z0-9_-]{1,40}")||key==null||key.length!=32||MessageDigest.isEqual(hashKey,key))throw new IllegalArgumentException("PHONE_KEY_POLICY_REQUIRED");copy.put(name,key.clone());});
  this.hashVersion=hashVersion;this.hashKey=hashKey.clone();this.active=active;this.keys=Map.copyOf(copy);
 }
 public int hashVersion(){return hashVersion;}
 public String fingerprint(String phone){return hmac("PHONE:"+hashVersion+":"+PhoneValues.phone(phone));}
 public String proof(UUID id,String hash,String code){if(id==null||hash==null||!hash.matches("[a-f0-9]{64}")||code==null||!code.matches("[0-9]{6}"))throw rejected();return hmac("PROOF:"+id+":"+hash+":"+code);}
 public boolean matches(UUID id,String hash,String code,String expected){return expected!=null&&expected.matches("[a-f0-9]{64}")&&MessageDigest.isEqual(HexFormat.of().parseHex(proof(id,hash,code)),HexFormat.of().parseHex(expected));}
 public String code(){return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));}
 private String hmac(String input){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(hashKey,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException failure){throw rejected();}}
 private byte[] aad(String key,UUID id,String kind){if(id==null||id.equals(new UUID(0,0))||!Set.of("ADDRESS","PROOF").contains(kind))throw rejected();return ("NIGPHONE1:"+key+":"+id+":"+kind).getBytes(StandardCharsets.US_ASCII);}
 public byte[] encrypt(UUID id,String kind,String value){try{if(value==null||value.isEmpty()||value.length()>32)throw rejected();byte[] nonce=new byte[12];random.nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(active),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(active,id,kind));return ("NIGPHONE1."+active+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)))).getBytes(StandardCharsets.US_ASCII);}catch(Exception failure){throw rejected();}}
 public String decrypt(UUID id,String kind,byte[] value){try{if(value==null||value.length>256)throw rejected();var parts=new String(value,StandardCharsets.US_ASCII).split("\\.",-1);if(parts.length!=4||!parts[0].equals("NIGPHONE1")||!keys.containsKey(parts[1]))throw rejected();byte[] nonce=Base64.getUrlDecoder().decode(parts[2]);if(nonce.length!=12)throw rejected();var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keys.get(parts[1]),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(parts[1],id,kind));String raw=new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[3])),StandardCharsets.UTF_8);if(raw.isEmpty()||raw.length()>32)throw rejected();return raw;}catch(Exception failure){throw rejected();}}
 private static IllegalArgumentException rejected(){return new IllegalArgumentException("PHONE_SECRET_REJECTED");}
 @Override public String toString(){return "PhoneSecrets[protected]";}
}
