package com.nigson.shared.security;
import java.security.*;
import java.security.spec.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;
/** Pinned public keys with bounded rotation overlap; no request-selected JWKS or network fetch. */
public final class ActorPublicKeys {
    private ActorPublicKeys(){}
    public static boolean p256(ECPublicKey key){try{var parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));var expected=parameters.getParameterSpec(ECParameterSpec.class);var actual=key.getParams();return actual.getCurve().equals(expected.getCurve())&&actual.getOrder().equals(expected.getOrder())&&actual.getGenerator().equals(expected.getGenerator())&&actual.getCofactor()==expected.getCofactor();}catch(Exception e){return false;}}
    public static Map<String,ECPublicKey> parse(String json){
        try{if(json==null||json.length()>8192)throw new IllegalArgumentException();var tree=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build().readTree(json);if(!tree.isObject()||tree.size()<1||tree.size()>4)throw new IllegalArgumentException();var keys=new HashMap<String,ECPublicKey>();for(var entry:tree.properties()){if(!entry.getKey().matches("[A-Za-z0-9_-]{1,40}")||!entry.getValue().isString())throw new IllegalArgumentException();var key=(ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(entry.getValue().asString())));if(!p256(key))throw new IllegalArgumentException();keys.put(entry.getKey(),key);}return Map.copyOf(keys);}catch(Exception e){throw new IllegalArgumentException("ACTOR_PUBLIC_KEYS_REQUIRED");}
    }
}
