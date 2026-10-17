package com.nigson.integration.notification;

import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import com.nigson.integration.provider.WorkloadIdentity;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.integration.providers.TwilioAdapter;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.security.ActorPublicKeys;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_MESSAGING_STAFF_REPLY_ENABLED",havingValue="true")
public class LeadReplyCommandConfiguration {
    private static void prerequisites(Environment e){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false)||!e.getProperty("NIGSON_MESSAGING_VERIFICATION_INTAKE_ENABLED",Boolean.class,false))throw new IllegalArgumentException("STAFF_REPLY_PREREQUISITES_REQUIRED");}
    @Bean StaffReplySource staffReplySource(Environment e){prerequisites(e);var identity=new WorkloadIdentity(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_OIDC_ISSUER"),e.getRequiredProperty("NIGSON_INTEGRATION_WORKLOAD_SECRET"),e.getProperty("NIGSON_PRODUCT_PHASE",Integer.class,1),Clock.systemUTC());return new StaffReplySource(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_EXPERIENCE_BASE_URL"),identity::token,Clock.systemUTC());}
    @Bean StaffReplyCrypto staffReplyCrypto(Environment e){prerequisites(e);String env=e.getRequiredProperty("APP_ENV"),cipherRevision=e.getRequiredProperty("NIGSON_MESSAGING_STAFF_REPLY_CIPHER_REVISION"),cipherArn=e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_CIPHER_KMS_KEY_ARN",""),wrappedCipher=e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_WRAPPED_CIPHER_KEY",""),localCipher=e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_LOCAL_CIPHER_KEY","");byte[] cipher=loadKey(env,cipherRevision,cipherArn,wrappedCipher,localCipher,"staff-reply-cipher");String requestRevision=e.getRequiredProperty("NIGSON_MESSAGING_REQUEST_KEY_REVISION"),requestArn=e.getProperty("NIGSON_MESSAGING_REQUEST_KMS_KEY_ARN",""),wrappedRequest=e.getProperty("NIGSON_MESSAGING_WRAPPED_REQUEST_KEY",""),localRequest=e.getProperty("NIGSON_MESSAGING_LOCAL_REQUEST_KEY","");byte[] request=loadRequestKey(env,requestRevision,requestArn,wrappedRequest,localRequest);return new StaffReplyCrypto(cipherRevision,cipher,request);}
    @Bean LeadReplyCommandService leadReplyCommandService(JdbcTemplate jdbc,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager manager,StaffReplySource source,StaffReplyCrypto crypto,TwilioAdapter provider,Environment e){prerequisites(e);String raw=e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_TEMPLATES","");var allowed=raw.isBlank()?Set.<String>of():new HashSet<>(List.of(raw.split(",",-1)));if(allowed.size()>10||allowed.stream().anyMatch(t->!t.equals("STAFF_LEAD_REPLY")))throw new IllegalArgumentException("STAFF_REPLY_TEMPLATE_POLICY_REQUIRED");String issuer=e.getRequiredProperty("NIGSON_OIDC_ISSUER");return new LeadReplyCommandService(jdbc,audit,metrics,manager,source,crypto,provider,Clock.systemUTC(),ApiValues.uuid(e.getRequiredProperty("NIGSON_MESSAGING_CREATION_GENERATION")),e.getRequiredProperty("NIGSON_MESSAGING_LOCALE"),e.getRequiredProperty("NIGSON_MESSAGING_STAFF_REPLY_TEMPLATE_REVISION",Integer.class),ActorPublicKeys.parse(e.getRequiredProperty("NIGSON_ACTOR_PUBLIC_KEYS")),Set.of(issuer),allowed,e.getRequiredProperty("NIGSON_MESSAGING_STAFF_REPLY_PROVIDER_TEMPLATE_ID"));}

    private static byte[] loadKey(String env,String revision,String arn,String wrapped,String local,String kind){if(!local.isBlank()){if(!Set.of("local","test").contains(env)||!arn.isBlank()||!wrapped.isBlank()||revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}"))throw new IllegalArgumentException("STAFF_REPLY_KEY_POLICY_REQUIRED");try{byte[] key=Base64.getDecoder().decode(local);if(key.length!=32)throw new IllegalArgumentException();return key;}catch(RuntimeException invalid){throw new IllegalArgumentException("STAFF_REPLY_KEY_POLICY_REQUIRED");}}if(revision==null||!revision.matches("[A-Za-z0-9_-]{1,40}")||!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}")||wrapped.isBlank()||wrapped.length()>12000)throw new IllegalArgumentException("STAFF_REPLY_KEY_POLICY_REQUIRED");try(var kms=KmsClient.create()){var result=kms.decrypt(DecryptRequest.builder().keyId(arn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(wrapped))).encryptionContext(Map.of("purpose","nigson-integration-notification","kind",kind,"revision",revision)).build());if(!arn.equals(result.keyId())||result.plaintext().asByteArray().length!=32)throw new IllegalArgumentException();return result.plaintext().asByteArray();}catch(RuntimeException failure){throw new IllegalStateException("STAFF_REPLY_KEY_UNAVAILABLE");}}
    private static byte[] loadRequestKey(String env,String revision,String arn,String wrapped,String local){if(!local.isBlank())return MessageKey.load(null,env,revision,arn,wrapped,local);try(var kms=KmsClient.create()){return MessageKey.load(kms,env,revision,arn,wrapped,local);}catch(RuntimeException failure){throw new IllegalStateException("STAFF_REPLY_REQUEST_KEY_UNAVAILABLE");}}
}
