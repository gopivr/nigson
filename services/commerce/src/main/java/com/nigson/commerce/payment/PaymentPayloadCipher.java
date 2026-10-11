package com.nigson.commerce.payment;

import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Owned AES-256-GCM receipt encryption with bounded rotation and contextual authentication. */
public final class PaymentPayloadCipher {
 private final String active;private final Map<String,byte[]> keys;private final SecureRandom random=new SecureRandom();
 public PaymentPayloadCipher(String active,Map<String,byte[]> keys){if(active==null||!active.matches("[A-Za-z0-9_-]{1,40}")||keys==null||keys.isEmpty()||keys.size()>3||!keys.containsKey(active))throw new IllegalArgumentException("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");var copied=new HashMap<String,byte[]>();keys.forEach((id,key)->{if(id==null||!id.matches("[A-Za-z0-9_-]{1,40}")||key==null||key.length!=32)throw new IllegalArgumentException("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");copied.put(id,key.clone());});this.active=active;this.keys=Map.copyOf(copied);}
 private byte[] context(String revision,UUID receipt,String account,String mode,String hash){if(receipt==null||receipt.equals(new UUID(0,0))||account==null||!account.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,99}")||!Set.of("MOCK","TEST","LIVE").contains(mode)||hash==null||!hash.matches("[a-f0-9]{64}"))throw rejected();return ("NIGPAY1:"+revision+":"+receipt+":"+account+":"+mode+":"+hash).getBytes(StandardCharsets.US_ASCII);}
 private String digest(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
 public byte[] encrypt(UUID receipt,String account,String mode,String hash,byte[] raw){try{if(raw==null||raw.length==0||raw.length>262144||!digest(raw).equals(hash))throw rejected();byte[] nonce=new byte[12];random.nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(active),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(context(active,receipt,account,mode,hash));byte[] encrypted=cipher.doFinal(raw);return ("NIGPAY1."+active+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)).getBytes(StandardCharsets.US_ASCII);}catch(Exception invalid){throw rejected();}}
 public byte[] decrypt(UUID receipt,String account,String mode,String hash,byte[] encoded){try{if(encoded==null||encoded.length>350000)throw rejected();String[] parts=new String(encoded,StandardCharsets.US_ASCII).split("\\.",-1);if(parts.length!=4||!parts[0].equals("NIGPAY1")||!keys.containsKey(parts[1]))throw rejected();byte[] nonce=Base64.getUrlDecoder().decode(parts[2]);if(nonce.length!=12)throw rejected();var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keys.get(parts[1]),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(context(parts[1],receipt,account,mode,hash));byte[] raw=cipher.doFinal(Base64.getUrlDecoder().decode(parts[3]));if(raw.length==0||raw.length>262144||!digest(raw).equals(hash))throw rejected();return raw;}catch(Exception invalid){throw rejected();}}
 private static IllegalArgumentException rejected(){return new IllegalArgumentException("PAYMENT_RECEIPT_CIPHER_REJECTED");}
}
