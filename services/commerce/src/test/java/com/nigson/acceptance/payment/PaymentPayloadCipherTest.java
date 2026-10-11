package com.nigson.acceptance.payment;

import java.util.*;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.nigson.commerce.payment.PaymentPayloadCipher;

class PaymentPayloadCipherTest {
 private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 @Test void encryptedRawReceiptBindsIdentityAccountModeAndDigestAndSupportsRotation()throws Exception{
  byte[] key=new byte[32];Arrays.fill(key,(byte)7);byte[] next=new byte[32];Arrays.fill(next,(byte)8);byte[] raw="{\"event\":\"charge.success\",\"data\":{\"id\":1}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);UUID receipt=UUID.randomUUID();String hash=hash(raw);var old=new PaymentPayloadCipher("r1",Map.of("r1",key));byte[] cipher=old.encrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,raw);
  assertThat(new String(cipher,java.nio.charset.StandardCharsets.US_ASCII)).doesNotContain(new String(raw,java.nio.charset.StandardCharsets.UTF_8));assertThat(old.decrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,cipher)).isEqualTo(raw);assertThat(new PaymentPayloadCipher("r2",Map.of("r1",key,"r2",next)).decrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,cipher)).isEqualTo(raw);
  assertThatThrownBy(()->old.decrypt(UUID.randomUUID(),"SYNTHETIC_ACCOUNT","MOCK",hash,cipher)).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");assertThatThrownBy(()->old.decrypt(receipt,"SYNTHETIC_OTHER_ACCOUNT","MOCK",hash,cipher)).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");assertThatThrownBy(()->old.decrypt(receipt,"SYNTHETIC_ACCOUNT","LIVE",hash,cipher)).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");assertThatThrownBy(()->old.decrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK","a".repeat(64),cipher)).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");cipher[cipher.length-1]^=1;assertThatThrownBy(()->old.decrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,cipher)).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");
 }
 @Test void maximumContractPayloadIsAcceptedAndOversizeFails()throws Exception{
  byte[] raw=new byte[262144];Arrays.fill(raw,(byte)'x');var codec=new PaymentPayloadCipher("r1",Map.of("r1",new byte[32]));UUID receipt=UUID.randomUUID();String hash=hash(raw);assertThat(codec.decrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,codec.encrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,raw))).isEqualTo(raw);assertThatThrownBy(()->codec.encrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK",hash,new byte[262145])).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");assertThatThrownBy(()->codec.encrypt(receipt,"SYNTHETIC_ACCOUNT","MOCK","b".repeat(64),raw)).hasMessage("PAYMENT_RECEIPT_CIPHER_REJECTED");
 }
}
