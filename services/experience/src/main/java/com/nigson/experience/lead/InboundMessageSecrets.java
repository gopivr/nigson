package com.nigson.experience.lead;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Purpose-separated AES-GCM protection for signed inbound message content. */
public final class InboundMessageSecrets {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public InboundMessageSecrets(byte[] key) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("INBOUND_KEY_POLICY_REQUIRED");
        this.key = key.clone();
    }
    public byte[] encrypt(UUID id, String kind, String value) {
        if (id == null || value == null || value.isEmpty() || value.length() > 4096 || !kind.matches("SENDER|BODY")) throw rejected();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("NIGINBOUND1:" + id + ":" + kind).getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, output, 0, nonce.length); System.arraycopy(encrypted, 0, output, nonce.length, encrypted.length);
            return output;
        } catch (Exception failure) { throw rejected(); }
    }
    public String decrypt(UUID id, String kind, byte[] value) {
        if (id == null || value == null || value.length < 29 || value.length > 8192 || !kind.matches("SENDER|BODY")) throw rejected();
        try {
            byte[] nonce=java.util.Arrays.copyOfRange(value,0,12), encrypted=java.util.Arrays.copyOfRange(value,12,value.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(("NIGINBOUND1:"+id+":"+kind).getBytes(StandardCharsets.US_ASCII));String result=new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
            if(result.isEmpty()||result.length()>4096)throw rejected();return result;
        } catch(Exception failure){throw rejected();}
    }
    private static IllegalArgumentException rejected() { return new IllegalArgumentException("INBOUND_CONTENT_REJECTED"); }
}
