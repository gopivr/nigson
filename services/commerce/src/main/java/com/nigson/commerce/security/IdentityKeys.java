package com.nigson.commerce.security;

import com.nigson.shared.security.ActorAssertion;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jwt.SignedJWT;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.*;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;
import software.amazon.awssdk.core.SdkBytes;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Dedicated KMS keys: asymmetric actor signing, separate symmetric session encryption. */
public final class IdentityKeys {
    private final KmsClient kms;private final String keyArn,kid;private final ECPublicKey publicKey;private final Clock clock;
    public IdentityKeys(KmsClient kms,String keyArn,String kid,ECPublicKey publicKey,Clock clock) {
        if(!keyArn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}")||!kid.matches("[A-Za-z0-9_-]{1,40}")||!com.nigson.shared.security.ActorPublicKeys.p256(publicKey))throw new IllegalArgumentException("ACTOR_KEY_POLICY_REQUIRED");
        this.kms=kms;this.keyArn=keyArn;this.kid=kid;this.publicKey=publicKey;this.clock=clock;
    }
    public String sign(ActorAssertion.Actor actor,ActorAssertion.Binding binding) {
        outsideTransaction();
        try {
            var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(kid).type(new JOSEObjectType("nigson-actor+jwt")).build(),ActorAssertion.claims(actor,binding,clock.instant()));
            var digest=MessageDigest.getInstance("SHA-256").digest(jwt.getSigningInput());
            var response=kms.sign(SignRequest.builder().keyId(keyArn).signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256).messageType(MessageType.DIGEST).message(SdkBytes.fromByteArray(digest)).build());
            if(!keyArn.equals(response.keyId())||response.signingAlgorithm()!=SigningAlgorithmSpec.ECDSA_SHA_256)throw new IllegalArgumentException();
            String token=new String(jwt.getSigningInput(),java.nio.charset.StandardCharsets.US_ASCII)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(ECDSA.transcodeSignatureToConcat(response.signature().asByteArray(),64));
            if(!SignedJWT.parse(token).verify(new ECDSAVerifier(publicKey)))throw new IllegalArgumentException();return token;
        }catch(Exception failure){throw new IllegalStateException("ACTOR_SIGNING_UNAVAILABLE");}
    }
    public static byte[] unwrap(KmsClient kms,String keyArn,String encrypted,String revision) {
        outsideTransaction();if(revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}")||encrypted==null||encrypted.length()>12000)throw new IllegalArgumentException("SESSION_KEY_POLICY_REQUIRED");
        try {var response=kms.decrypt(DecryptRequest.builder().keyId(keyArn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(encrypted))).encryptionContext(Map.of("purpose","nigson-commerce-session","revision",revision)).build());
            if(!keyArn.equals(response.keyId())||response.plaintext().asByteArray().length!=32)throw new IllegalArgumentException();return response.plaintext().asByteArray();
        }catch(Exception e){throw new IllegalStateException("SESSION_KEY_UNAVAILABLE");}
    }
    public static void outsideTransaction(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("IDENTITY_HTTP_OUTSIDE_TRANSACTION_REQUIRED");}
}
