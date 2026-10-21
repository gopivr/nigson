package com.nigson.experience.lead;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Local/test anti-abuse verifier and bounded rate limiter; never selected in deployed environments. */
public final class LocalLeadMocks {
    private LocalLeadMocks() {}
    public static final class AntiSpam implements LeadService.AntiSpamVerifier {
        private final byte[] key;
        public AntiSpam(String encoded){try{key=Base64.getDecoder().decode(encoded);if(key.length<32)throw new IllegalArgumentException();}catch(RuntimeException invalid){throw new IllegalArgumentException("LEAD_MOCK_ANTISPAM_KEY_REQUIRED");}}
        public String sign(String nonce,String idempotencyKey){if(nonce==null||!nonce.matches("[A-Za-z0-9_-]{16,80}"))throw new IllegalArgumentException("LEAD_MOCK_NONCE_INVALID");return nonce+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key,nonce+"\n"+idempotencyKey));}
        @Override public boolean verify(String token,String idempotencyKey){try{if(token==null||token.length()>512||idempotencyKey==null)return false;String[] p=token.split("\\.",-1);if(p.length!=2||!p[0].matches("[A-Za-z0-9_-]{16,80}"))return false;byte[] supplied=Base64.getUrlDecoder().decode(p[1]);return java.security.MessageDigest.isEqual(supplied,mac(key,p[0]+"\n"+idempotencyKey));}catch(RuntimeException bad){return false;}}
        @Override public String toString(){return "LocalLeadAntiSpamMock[protected]";}
    }
    public static final class RateLimit implements LeadService.RateLimiter {
        private record Window(long start,int count) {}
        private final int limit;private final Clock clock;private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
        public RateLimit(int limit,Clock clock){if(limit<1||limit>1000)throw new IllegalArgumentException("LEAD_RATE_LIMIT_INVALID");this.limit=limit;this.clock=Objects.requireNonNull(clock);}
        @Override public boolean allow(String source){if(source==null||source.isBlank()||source.length()>256)return false;long minute=clock.instant().getEpochSecond()/60;if(windows.size()>8192)windows.entrySet().removeIf(e->e.getValue().start()<minute-1);if(windows.size()>8192&&!windows.containsKey(source))return false;var next=windows.compute(source,(k,old)->old==null||old.start()!=minute?new Window(minute,1):new Window(minute,old.count()+1));return next.count()<=limit;}
    }
    private static byte[] mac(byte[] key,String value){try{var h=Mac.getInstance("HmacSHA256");h.init(new SecretKeySpec(key,"HmacSHA256"));return h.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("LEAD_ANTISPAM_UNAVAILABLE");}}
}
