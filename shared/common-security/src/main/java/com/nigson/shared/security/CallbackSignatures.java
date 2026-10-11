package com.nigson.shared.security;

import com.twilio.security.RequestValidator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Signature protocols only. Receipts, replay and financial effects stay in the owner. */
public final class CallbackSignatures {
 private CallbackSignatures(){}
 public static boolean paystack(byte[] raw,String signature,String key) {
  if(raw==null||raw.length>262144||signature==null||!signature.matches("[a-fA-F0-9]{128}"))return false;
  try {var mac=Mac.getInstance("HmacSHA512");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));return MessageDigest.isEqual(mac.doFinal(raw),HexFormat.of().parseHex(signature));}catch(Exception failure){return false;}
 }
 /** externalUrl is the configured public callback URL, never Host/X-Forwarded-* supplied by a caller. */
 public static boolean twilio(String externalUrl,Map<String,String> completeParameters,String signature,String token) {
  if(signature==null||externalUrl==null||externalUrl.length()>2048||completeParameters.size()>100||completeParameters.entrySet().stream().anyMatch(e->e.getKey().length()>100||e.getValue().length()>4096))return false;
  return new RequestValidator(token).validate(externalUrl,completeParameters,signature);
 }
}
