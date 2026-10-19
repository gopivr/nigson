package com.nigson.experience.consent;

import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.experience.platform.*;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration(proxyBeanMethods=false)
public class ConsentConfiguration {
 @Bean @ConditionalOnProperty(name="NIGSON_CONSENT_ENABLED",havingValue="true") ConsentSecrets consentSecrets(Environment e){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CONSENT_SECURITY_REQUIRED");String env=e.getRequiredProperty("APP_ENV"),revision=e.getRequiredProperty("NIGSON_CONSENT_CIPHER_REVISION"),localHash=e.getProperty("NIGSON_CONSENT_LOCAL_HASH_KEY",""),localCipher=e.getProperty("NIGSON_CONSENT_LOCAL_CIPHER_KEY","");if(!localHash.isBlank()||!localCipher.isBlank())return ConsentKeys.load(null,env,e.getRequiredProperty("NIGSON_CONSENT_HASH_KEY_VERSION",Integer.class),revision,e.getProperty("NIGSON_CONSENT_KMS_KEY_ARN",""),e.getProperty("NIGSON_CONSENT_WRAPPED_HASH_KEY",""),e.getProperty("NIGSON_CONSENT_WRAPPED_CIPHER_KEY",""),localHash,localCipher,e.getProperty("NIGSON_CONSENT_PREVIOUS_CIPHER_KEYS","{}"));try(var kms=KmsClient.create()){return ConsentKeys.load(kms,env,e.getRequiredProperty("NIGSON_CONSENT_HASH_KEY_VERSION",Integer.class),revision,e.getProperty("NIGSON_CONSENT_KMS_KEY_ARN",""),e.getProperty("NIGSON_CONSENT_WRAPPED_HASH_KEY",""),e.getProperty("NIGSON_CONSENT_WRAPPED_CIPHER_KEY",""),localHash,localCipher,e.getProperty("NIGSON_CONSENT_PREVIOUS_CIPHER_KEYS","{}"));}}
 @Bean @ConditionalOnProperty(name="NIGSON_CONSENT_ANTISPAM_MODE",havingValue="mock") NewsletterService.AntiSpamVerifier localConsentAntiSpam(Environment e){if(!Set.of("local","test").contains(e.getRequiredProperty("APP_ENV")))throw new IllegalArgumentException("CONSENT_MOCK_ANTISPAM_LOCAL_ONLY");return new LocalConsentAntiSpamMock(e.getRequiredProperty("NIGSON_CONSENT_LOCAL_ANTISPAM_HMAC_KEY"));}
 @Bean NewsletterService newsletterService(JdbcTemplate jdbc,PlatformStore store,AuditWriter audit,org.springframework.beans.factory.ObjectProvider<ConsentSecrets> secrets,PlatformTransactionManager manager,Environment e,org.springframework.beans.factory.ObjectProvider<NewsletterService.AntiSpamVerifier> antiSpam){String policies=e.getProperty("NIGSON_CONSENT_POLICY_VERSIONS","");Set<String> approved=policies.isBlank()?Set.of():Set.copyOf(Arrays.asList(policies.split(",",-1)));return new NewsletterService(jdbc,store,audit,secrets.getIfAvailable(),manager,Clock.systemUTC(),e.getProperty("NIGSON_CONSENT_POLICY_VERSION",""),approved,e.getProperty("NIGSON_PUBLIC_WEB_BASE_URL",""),antiSpam.getIfAvailable());}
 @Bean ConsentExpiry consentExpiry(NewsletterService service){return new ConsentExpiry(service);}
 public static final class ConsentExpiry {private final NewsletterService service;ConsentExpiry(NewsletterService service){this.service=service;}@org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${NIGSON_CONSENT_EXPIRY_INTERVAL_MS:60000}")public void run(){service.expire(100);}}
}
