package com.nigson.experience.consent;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
/** Experience-owned distinct KMS purposes; no keys from sessions, payment receipts or provider credentials. */
public final class PhoneKeys {
 private PhoneKeys(){}
 public static PhoneSecrets load(KmsClient kms,String env,int hashVersion,String revision,String arn,String wrappedHash,String wrappedCipher,String localHash,String localCipher,String previous){
  if(!Set.of("local","test","dev","qa","stage","prod").contains(env)||hashVersion<1||revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}")||previous==null||previous.length()>25000)throw policy();
  var json=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();var prior=json.readTree(previous);if(prior==null||!prior.isObject()||prior.size()>2)throw policy();var keys=new HashMap<String,byte[]>();
  if(!localHash.isBlank()||!localCipher.isBlank()){if(!Set.of("local","test").contains(env)||!arn.isBlank()||!wrappedHash.isBlank()||!wrappedCipher.isBlank()||!prior.isEmpty())throw policy();try{keys.put(revision,Base64.getDecoder().decode(localCipher));return new PhoneSecrets(hashVersion,Base64.getDecoder().decode(localHash),revision,keys);}catch(IllegalArgumentException invalid){throw policy();}}
  if(kms==null||!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}"))throw policy();byte[] hash=unwrap(kms,arn,wrappedHash,"lookup-proof",Integer.toString(hashVersion));keys.put(revision,unwrap(kms,arn,wrappedCipher,"cipher",revision));for(var e:prior.properties()){if(!e.getKey().matches("[A-Za-z0-9_-]{1,40}")||keys.containsKey(e.getKey())||!e.getValue().isString())throw policy();keys.put(e.getKey(),unwrap(kms,arn,e.getValue().asString(),"cipher",e.getKey()));}return new PhoneSecrets(hashVersion,hash,revision,keys);
 }
 private static byte[] unwrap(KmsClient kms,String arn,String wrapped,String kind,String revision){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PHONE_KEY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");if(wrapped==null||wrapped.isBlank()||wrapped.length()>12000)throw policy();try{var reply=kms.decrypt(DecryptRequest.builder().keyId(arn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(wrapped))).encryptionContext(Map.of("purpose","nigson-experience-phone","kind",kind,"revision",revision)).build());if(!arn.equals(reply.keyId())||reply.plaintext().asByteArray().length!=32)throw policy();return reply.plaintext().asByteArray();}catch(Exception failure){throw new IllegalStateException("PHONE_KEY_UNAVAILABLE");}}
 private static IllegalArgumentException policy(){return new IllegalArgumentException("PHONE_KEY_POLICY_REQUIRED");}
}
