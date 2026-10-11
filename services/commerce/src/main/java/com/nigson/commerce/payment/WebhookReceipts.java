package com.nigson.commerce.payment;
import java.util.*;
/** Owned durable encrypted receipts; callers authenticate raw bytes before persistence. */
public interface WebhookReceipts {
 record Receipt(UUID id,String eventKey,String bodyHash,String state,String rejectionCode,long version){}
 record Stored(Receipt receipt,boolean created){}
 record Pending(UUID id,String merchantAccount,String bodyHash,byte[] ciphertext,boolean verifiedSignature){public Pending{ciphertext=ciphertext.clone();}public byte[] ciphertext(){return ciphertext.clone();}}
 record Verification(UUID order,UUID attempt,UUID refund,String providerObjectId,String providerMode){}
 Stored receive(UUID id,String account,PaystackWebhookProtocol.Event event,byte[] ciphertext);
 Pending pending(UUID id);
 List<UUID> pending(int limit);
 Receipt outcome(UUID id,String account,String bodyHash,String state,String rejectionCode,Verification verification);
}
