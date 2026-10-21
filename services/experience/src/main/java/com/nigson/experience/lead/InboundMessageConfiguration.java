package com.nigson.experience.lead;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import com.nigson.experience.platform.*;
import com.nigson.experience.consent.PhoneSecrets;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_MESSAGING_INBOUND_ENABLED",havingValue="true")
public class InboundMessageConfiguration {
    @Bean InboundMessageSecrets inboundMessageSecrets(Environment e) {
        if (!e.getProperty("NIGSON_SECURITY_ENABLED", Boolean.class, false)) throw new IllegalArgumentException("INBOUND_SECURITY_REQUIRED");
        String env=e.getRequiredProperty("APP_ENV"),encoded=e.getProperty("NIGSON_INBOUND_LOCAL_CIPHER_KEY","");
        if(!encoded.isBlank()) {if(!java.util.Set.of("local","test").contains(env)||!e.getProperty("NIGSON_INBOUND_KMS_KEY_ARN","").isBlank()||!e.getProperty("NIGSON_INBOUND_WRAPPED_CIPHER_KEY","").isBlank())throw new IllegalArgumentException("INBOUND_KEY_POLICY_REQUIRED");try{return new InboundMessageSecrets(Base64.getDecoder().decode(encoded));}catch(RuntimeException failure){throw new IllegalArgumentException("INBOUND_KEY_POLICY_REQUIRED");}}
        String arn=e.getRequiredProperty("NIGSON_INBOUND_KMS_KEY_ARN"),wrapped=e.getRequiredProperty("NIGSON_INBOUND_WRAPPED_CIPHER_KEY"),revision=e.getRequiredProperty("NIGSON_INBOUND_CIPHER_REVISION");
        if(!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}")||!revision.matches("[A-Za-z0-9_-]{1,40}"))throw new IllegalArgumentException("INBOUND_KEY_POLICY_REQUIRED");
        try(var kms=KmsClient.create()){var result=kms.decrypt(DecryptRequest.builder().keyId(arn).ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(wrapped))).encryptionContext(java.util.Map.of("purpose","nigson-experience-inbound","kind","cipher","revision",revision)).build());if(!arn.equals(result.keyId()))throw new IllegalArgumentException("INBOUND_KEY_POLICY_REQUIRED");return new InboundMessageSecrets(result.plaintext().asByteArray());}catch(RuntimeException failure){throw new IllegalStateException("INBOUND_KEY_UNAVAILABLE");}
    }
    @Bean InboundMessageProcessor inboundMessageProcessor(JdbcTemplate jdbc, AuditWriter audit, PlatformStore store,
        PhoneSecrets phones, InboundMessageSecrets secrets, MeterRegistry metrics, Environment e, PlatformTransactionManager manager) {
        return new InboundMessageProcessor(jdbc,audit,store,phones,secrets,metrics,Clock.systemUTC(),manager,
            e.getRequiredProperty("NIGSON_TWILIO_ACCOUNT_SID"),e.getRequiredProperty("NIGSON_TWILIO_AUTH_TOKEN"),
            e.getRequiredProperty("NIGSON_TWILIO_WHATSAPP_FROM"),e.getRequiredProperty("NIGSON_TWILIO_WHATSAPP_PUBLIC_URL"),
            e.getRequiredProperty("NIGSON_INBOUND_RETENTION_DAYS",Integer.class),e.getRequiredProperty("NIGSON_LEAD_RECIPIENT_GROUP"));
    }
    @Bean InboundMessageController inboundMessageController(InboundMessageProcessor processor) { return new InboundMessageController(processor); }
    @Bean LeadMessagingContext leadMessagingContext(JdbcTemplate jdbc,com.nigson.experience.consent.PhoneSecrets phones,InboundMessageSecrets content,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager manager,Environment e){String raw=e.getProperty("NIGSON_MESSAGING_STAFF_REPLY_TEMPLATES","");var templates=raw.isBlank()?java.util.List.<String>of():java.util.List.of(raw.split(",",-1));return new LeadMessagingContext(jdbc,phones,content,audit,metrics,Clock.systemUTC(),manager,templates);}
    @Bean LeadMessagingContextController leadMessagingContextController(LeadMessagingContext service){return new LeadMessagingContextController(service);}
    @Bean LeadMessagingSuppressionCheck leadMessagingSuppressionCheck(JdbcTemplate jdbc,AuditWriter audit,MeterRegistry metrics,PlatformTransactionManager manager){return new LeadMessagingSuppressionCheck(jdbc,audit,metrics,manager);}
    @Bean LeadMessagingSuppressionController leadMessagingSuppressionController(LeadMessagingSuppressionCheck check){return new LeadMessagingSuppressionController(check);}
    @Bean InboundMessageRetention inboundMessageRetention(InboundMessageProcessor processor){return new InboundMessageRetention(processor);}
    public static final class InboundMessageRetention {private final InboundMessageProcessor processor;InboundMessageRetention(InboundMessageProcessor processor){this.processor=processor;}@org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${NIGSON_INBOUND_RETENTION_INTERVAL_MS:60000}") public void run(){processor.eraseExpired(100);}}
}
