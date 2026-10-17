package com.nigson.integration.notification;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.core.SdkBytes;
/** Dedicated integration-owned request HMAC key; never shared with phone proof or provider tokens. */
public final class MessageKey {
 private MessageKey(){}
 public static byte[] load(KmsClient kms,String environment,String revision,String arn,String wrapped,String local){if(environment==null||!Set.of("local","test","dev","qa","stage","prod").contains(environment)||revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}"))throw policy();if(!local.isBlank()){if(!Set.of("local","test").contains(environment)||!arn.isBlank()||!wrapped.isBlank())throw policy();try{byte[] key=Base64.getDecoder().decode(local);if(key.length!=32)throw policy();return key;}catch(IllegalArgumentException invalid){throw policy();}}if(kms==null||!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}")||wrapped.isBlank()||wrapped.length()>12000)throw policy();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("MESSAGE_KEY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");try{var response=kms.decrypt(r->r.keyId(arn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(wrapped))).encryptionContext(Map.of("purpose","nigson-integration-notification","kind","request-hmac","revision",revision)));byte[] key=response.plaintext().asByteArray();if(!arn.equals(response.keyId())||key.length!=32)throw policy();return key;}catch(Exception unavailable){throw new IllegalStateException("MESSAGE_KEY_UNAVAILABLE");}}
 private static IllegalArgumentException policy(){return new IllegalArgumentException("MESSAGE_KEY_POLICY_REQUIRED");}
}
