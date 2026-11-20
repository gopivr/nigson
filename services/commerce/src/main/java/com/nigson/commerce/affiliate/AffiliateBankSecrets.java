package com.nigson.commerce.affiliate;
import java.util.*;
import java.security.MessageDigest;
import com.nigson.commerce.payment.PaymentPayloadCipher;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

/** Uses existing Commerce AES-GCM keys, with bank-specific party/row binding. */
public final class AffiliateBankSecrets {
 public record Details(String bankName,String accountName,String accountNumber,String padding){@Override public String toString(){return "[private bank details]";}}
 public record Sealed(byte[] ciphertext,String hash,String mode){}
 private final java.util.function.Supplier<PaymentPayloadCipher> cipher;private final String mode;
 public AffiliateBankSecrets(java.util.function.Supplier<PaymentPayloadCipher> cipher,String mode){this.cipher=cipher;this.mode=mode;}
 private PaymentPayloadCipher required(){var value=cipher.get();if(value==null||!Set.of("MOCK","TEST","LIVE").contains(mode))throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);return value;}
 public Sealed seal(UUID id,UUID party,String bank,String name,String number){
  var engine=required();var raw=EventCodec.JSON.writeValueAsBytes(new Details(bank,name,number,UUID.randomUUID().toString()));
  // Secret random padding is encrypted, so the public commitment cannot enumerate account numbers.
  try{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));return new Sealed(engine.encrypt(id,"affiliate-bank:"+party,mode,hash,raw),hash,mode);}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 public Details open(UUID id,UUID party,byte[] ciphertext,String hash,String storedMode){var engine=required();return EventCodec.JSON.readValue(engine.decrypt(id,"affiliate-bank:"+party,storedMode,hash,ciphertext),Details.class);}
}
