package com.nigson.experience.consent;

import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;

/** KMS wrapping contract for consent-only lookup hashes and encryption keys. */
public final class ConsentKeys {
 private ConsentKeys(){}
 public static ConsentSecrets load(KmsClient kms,String env,int version,String revision,String arn,String wrappedHash,String wrappedCipher,String localHash,String localCipher,String previous){
  if(!Set.of("local","test","dev","qa","stage","prod").contains(env)||version<1||revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}")||previous==null||previous.length()>25000)throw policy();var json=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();var old=json.readTree(previous);if(old==null||!old.isObject()||old.size()>2)throw policy();var ciphers=new HashMap<String,byte[]>();
  if(!localHash.isBlank()||!localCipher.isBlank()){if(!Set.of("local","test").contains(env)||!arn.isBlank()||!wrappedHash.isBlank()||!wrappedCipher.isBlank()||!old.isEmpty())throw policy();try{ciphers.put(revision,Base64.getDecoder().decode(localCipher));return new ConsentSecrets(version,Base64.getDecoder().decode(localHash),revision,ciphers);}catch(Exception invalid){throw policy();}}
  if(kms==null||!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}"))throw policy();byte[] hash=unwrap(kms,arn,wrappedHash,"lookup-proof",Integer.toString(version));ciphers.put(revision,unwrap(kms,arn,wrappedCipher,"cipher",revision));for(var entry:old.properties()){if(!entry.getKey().matches("[A-Za-z0-9_-]{1,40}")||ciphers.containsKey(entry.getKey())||!entry.getValue().isString())throw policy();ciphers.put(entry.getKey(),unwrap(kms,arn,entry.getValue().asString(),"cipher",entry.getKey()));}return new ConsentSecrets(version,hash,revision,ciphers);
 }
 private static byte[] unwrap(KmsClient kms,String arn,String wrapped,String kind,String revision){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("CONSENT_KEY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");if(wrapped==null||wrapped.isBlank()||wrapped.length()>12000)throw policy();try{var result=kms.decrypt(DecryptRequest.builder().keyId(arn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(wrapped))).encryptionContext(Map.of("purpose","nigson-experience-consent","kind",kind,"revision",revision)).build());if(!arn.equals(result.keyId())||result.plaintext().asByteArray().length!=32)throw policy();return result.plaintext().asByteArray();}catch(Exception failure){throw new IllegalStateException("CONSENT_KEY_UNAVAILABLE");}}
 private static IllegalArgumentException policy(){return new IllegalArgumentException("CONSENT_KEY_POLICY_REQUIRED");}
}
