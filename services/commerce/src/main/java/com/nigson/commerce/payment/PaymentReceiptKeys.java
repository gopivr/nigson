package com.nigson.commerce.payment;

import java.util.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Receipt keys have their own KMS purpose and cannot reuse the session encryption context. */
public final class PaymentReceiptKeys {
 private PaymentReceiptKeys(){}
 public static PaymentPayloadCipher load(KmsClient kms,String environment,String revision,String arn,String wrapped,String local,String previous){
  if(!Set.of("local","test","dev","qa","stage","prod").contains(environment)||revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}"))throw policy();
  if(previous==null||previous.length()>25000)throw policy();var keys=new HashMap<String,byte[]>();tools.jackson.databind.JsonNode prior;try{prior=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(previous);}catch(Exception invalid){throw policy();}if(prior==null||!prior.isObject()||prior.size()>2)throw policy();
  if(!local.isBlank()){
   if(!Set.of("local","test").contains(environment)||!arn.isBlank()||!wrapped.isBlank()||!prior.isEmpty())throw policy();
   try{keys.put(revision,Base64.getDecoder().decode(local));return new PaymentPayloadCipher(revision,keys);}catch(IllegalArgumentException invalid){throw policy();}
  }
  if(kms==null||!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}"))throw policy();
  keys.put(revision,unwrap(kms,arn,wrapped,revision));for(var e:prior.properties()){if(!e.getKey().matches("[A-Za-z0-9_-]{1,40}")||keys.containsKey(e.getKey())||!e.getValue().isString())throw policy();keys.put(e.getKey(),unwrap(kms,arn,e.getValue().asString(),e.getKey()));}return new PaymentPayloadCipher(revision,keys);
 }
 private static byte[] unwrap(KmsClient kms,String arn,String wrapped,String revision){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PAYMENT_KEY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");if(wrapped==null||wrapped.isBlank()||wrapped.length()>12000)throw policy();try{var response=kms.decrypt(DecryptRequest.builder().keyId(arn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(wrapped))).encryptionContext(Map.of("purpose","nigson-commerce-payment-receipts","revision",revision)).build());if(!arn.equals(response.keyId())||response.plaintext().asByteArray().length!=32)throw policy();return response.plaintext().asByteArray();}catch(Exception unavailable){throw new IllegalStateException("PAYMENT_RECEIPT_KEY_UNAVAILABLE");}}
 private static IllegalArgumentException policy(){return new IllegalArgumentException("PAYMENT_RECEIPT_KEY_POLICY_REQUIRED");}
}
