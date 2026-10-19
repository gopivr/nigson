package com.nigson.experience.consent;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Stateful, idempotency-bound anti-spam verifier available only in local/test. */
public final class LocalConsentAntiSpamMock implements NewsletterService.AntiSpamVerifier {
 private final byte[] key;private final ConcurrentHashMap<String,String> accepted=new ConcurrentHashMap<>();
 public LocalConsentAntiSpamMock(String base64){try{key=Base64.getDecoder().decode(base64);if(key.length<32)throw new IllegalArgumentException();}catch(Exception invalid){throw new IllegalArgumentException("CONSENT_MOCK_ANTISPAM_KEY_REQUIRED");}}
 public String sign(String nonce,String requestKey){if(nonce==null||!nonce.matches("[A-Za-z0-9_-]{16,100}")||requestKey==null||requestKey.isBlank()||requestKey.length()>100)throw new IllegalArgumentException("CONSENT_MOCK_ANTISPAM_INPUT_INVALID");return nonce+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(nonce+":"+requestKey));}
 public boolean verify(String token,String requestKey){try{if(token==null||token.length()>512||requestKey==null||requestKey.length()>100||accepted.size()>10000)return false;String[] p=token.split("\\.",-1);if(p.length!=2||!p[0].matches("[A-Za-z0-9_-]{16,100}"))return false;byte[] actual=Base64.getUrlDecoder().decode(p[1]);if(!MessageDigest.isEqual(actual,mac(p[0]+":"+requestKey)))return false;String identity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));String prior=accepted.putIfAbsent(identity,requestKey);return prior==null||prior.equals(requestKey);}catch(Exception invalid){return false;}}
 private byte[] mac(String input){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(("nigson-consent-antispam:v1:"+input).getBytes(StandardCharsets.US_ASCII));}catch(Exception failure){throw new IllegalStateException("CONSENT_MOCK_ANTISPAM_UNAVAILABLE");}}
 @Override public String toString(){return "LocalConsentAntiSpamMock[protected]";}
}
