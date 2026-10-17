package com.nigson.experience.consent;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nigson.experience.platform.*;
import com.nigson.shared.security.*;
import software.amazon.awssdk.services.kms.KmsClient;
@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class PhoneConfiguration {
 @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${NIGSON_PHONE_CONSENT_ENABLED:false}' == 'true' or '${NIGSON_MESSAGING_INBOUND_ENABLED:false}' == 'true'") PhoneSecrets phoneSecrets(Environment e){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("PHONE_SECURITY_REQUIRED");String env=e.getRequiredProperty("APP_ENV"),revision=e.getRequiredProperty("NIGSON_PHONE_CIPHER_REVISION"),arn=e.getProperty("NIGSON_PHONE_KMS_KEY_ARN",""),hash=e.getProperty("NIGSON_PHONE_WRAPPED_HASH_KEY",""),cipher=e.getProperty("NIGSON_PHONE_WRAPPED_CIPHER_KEY",""),localHash=e.getProperty("NIGSON_PHONE_LOCAL_HASH_KEY",""),localCipher=e.getProperty("NIGSON_PHONE_LOCAL_CIPHER_KEY",""),previous=e.getProperty("NIGSON_PHONE_PREVIOUS_CIPHER_KEYS","{}");int version=e.getRequiredProperty("NIGSON_PHONE_HASH_KEY_VERSION",Integer.class);if(!localHash.isBlank()||!localCipher.isBlank())return PhoneKeys.load(null,env,version,revision,arn,hash,cipher,localHash,localCipher,previous);try(var kms=KmsClient.create()){return PhoneKeys.load(kms,env,version,revision,arn,hash,cipher,localHash,localCipher,previous);}}
 @Bean @ConditionalOnProperty(name="NIGSON_PHONE_CONSENT_ENABLED",havingValue="true") PhoneConsent phoneConsent(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,PhoneSecrets secrets,PlatformTransactionManager manager,Environment environment){return new PhoneConsent(jdbc,store,audit,secrets,Clock.systemUTC(),manager,actor->{var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof PrivateWorkload.Proof proof)||!"nigson-commerce".equals(proof.client())||!actor.equals(proof.actor())||!proof.route().owner().equals("experience")||!proof.route().scope().endsWith("_WITH_SIGNED_CUSTOMER"))throw ActorAssertion.denied();},java.util.Set.copyOf(java.util.List.of(environment.getRequiredProperty("NIGSON_PHONE_POLICY_VERSIONS").split(",",-1))));}
 @Bean @ConditionalOnProperty(name="NIGSON_PHONE_CONSENT_ENABLED",havingValue="true") ProofExpiry phoneProofExpiry(PhoneConsent consent){return new ProofExpiry(consent);}
 public static final class ProofExpiry {private final PhoneConsent consent;ProofExpiry(PhoneConsent consent){this.consent=consent;}@Scheduled(fixedDelayString="${NIGSON_PHONE_EXPIRY_INTERVAL_MS:60000}")public void run(){consent.expire(100);}}
}
