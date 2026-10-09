package com.nigson.commerce.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import tools.jackson.databind.json.JsonMapper;

/** AES-256-GCM typed codec. Unwrapped keys are loaded once before any JDBC transaction. */
public final class SessionCodec {
    private final String active;private final Map<String,byte[]> keys;
    private final SecureRandom random=new SecureRandom();
    private static final JsonMapper JSON=JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(16384).maxNumberLength(20).build()).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public SessionCodec(String active,Map<String,byte[]> keys) {
        if(active==null||!active.matches("[A-Za-z0-9_-]{1,40}")||keys.isEmpty()||keys.size()>3||!keys.containsKey(active))throw new IllegalArgumentException("SESSION_KEY_POLICY_REQUIRED");
        var copy=new HashMap<String,byte[]>();keys.forEach((id,key)->{if(!id.matches("[A-Za-z0-9_-]{1,40}")||key.length!=32)throw new IllegalArgumentException("SESSION_KEY_POLICY_REQUIRED");copy.put(id,key.clone());});this.active=active;this.keys=Map.copyOf(copy);
    }
    public byte[] encode(Object record) {
        try {
            String type=record instanceof SessionRecord?"identity":record instanceof PendingLogin?"login":record instanceof String s&&s.matches("[0-9a-f-]{36}")?"principal":null;
            if(type==null)throw rejected();
            byte[] plain=JSON.writeValueAsBytes(record);if(plain.length>40000)throw rejected();byte[] nonce=new byte[12];random.nextBytes(nonce);
            String prefix="NIGSON1."+active+"."+type+".";var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(active),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(prefix.getBytes(StandardCharsets.US_ASCII));
            return (prefix+Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(plain))).getBytes(StandardCharsets.US_ASCII);
        }catch(Exception failure){throw rejected();}
    }
    public Object decode(byte[] bytes) {
        try {
            if(bytes==null||bytes.length>55000)throw rejected();String[] p=new String(bytes,StandardCharsets.US_ASCII).split("\\.",-1);
            if(p.length!=5||!"NIGSON1".equals(p[0])||!keys.containsKey(p[1]))throw rejected();byte[] nonce=Base64.getUrlDecoder().decode(p[3]);if(nonce.length!=12)throw rejected();
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keys.get(p[1]),"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD((p[0]+"."+p[1]+"."+p[2]+".").getBytes(StandardCharsets.US_ASCII));byte[] plain=cipher.doFinal(Base64.getUrlDecoder().decode(p[4]));
            return switch(p[2]){case "identity"->JSON.readValue(plain,SessionRecord.class);case "login"->JSON.readValue(plain,PendingLogin.class);case "principal"->{String principal=JSON.readValue(plain,String.class);if(!principal.matches("[0-9a-f-]{36}"))throw rejected();yield principal;}default->throw rejected();};
        }catch(Exception failure){throw rejected();}
    }
    public org.springframework.core.convert.ConversionService conversionService() {
        var conversions=new org.springframework.core.convert.support.GenericConversionService();
        conversions.addConverter(Object.class,byte[].class,this::encode);conversions.addConverter(byte[].class,Object.class,this::decode);return conversions;
    }
    private static IllegalArgumentException rejected(){return new IllegalArgumentException("SESSION_CODEC_REJECTED");}
}
