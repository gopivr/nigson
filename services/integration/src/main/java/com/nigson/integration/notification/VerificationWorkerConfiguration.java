package com.nigson.integration.notification;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.integration.provider.WorkloadIdentity;
import com.nigson.integration.providers.*;
import com.nigson.shared.problem.ApiValues;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.services.kms.KmsClient;
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="NIGSON_MESSAGING_VERIFICATION_WORKER_ENABLED",havingValue="true")
public class VerificationWorkerConfiguration {
 private void enabled(Environment e){for(String key:List.of("NIGSON_SECURITY_ENABLED","NIGSON_MESSAGING_VERIFICATION_INTAKE_ENABLED"))if(!e.getProperty(key,Boolean.class,false))throw new IllegalArgumentException("MESSAGE_WORKER_PREREQUISITES_REQUIRED");long delay=e.getProperty("NIGSON_MESSAGING_WORKER_DELAY_MS",Long.class,30000L);String approval=e.getRequiredProperty("NIGSON_MESSAGING_APPROVAL");if(delay<1000||delay>300000||!approval.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("MESSAGE_WORKER_POLICY_REQUIRED");}
 @Bean VerificationExecution verificationExecution(JdbcTemplate jdbc,PlatformTransactionManager manager,AuditWriter audit,TwilioAdapter provider,Environment e,MeterRegistry metrics){enabled(e);return new VerificationExecution(jdbc,manager,audit,Clock.systemUTC(),provider.mode(),provider.accountFingerprint(),ApiValues.uuid(e.getRequiredProperty("NIGSON_MESSAGING_CREATION_GENERATION")),Instant.parse(e.getRequiredProperty("NIGSON_MESSAGING_CREATION_NOT_BEFORE")),metrics);}
 @Bean VerificationContextClient verificationContextClient(Environment e){enabled(e);var identity=new WorkloadIdentity(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_OIDC_ISSUER"),e.getRequiredProperty("NIGSON_INTEGRATION_WORKLOAD_SECRET"),e.getProperty("NIGSON_PRODUCT_PHASE",Integer.class,1),Clock.systemUTC());return new VerificationContextClient(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("NIGSON_EXPERIENCE_BASE_URL"),identity::token,Clock.systemUTC());}
 @Bean VerificationMessage verificationMessage(Environment e){enabled(e);String env=e.getRequiredProperty("APP_ENV"),revision=e.getRequiredProperty("NIGSON_MESSAGING_REQUEST_KEY_REVISION"),arn=e.getProperty("NIGSON_MESSAGING_REQUEST_KMS_KEY_ARN",""),wrapped=e.getProperty("NIGSON_MESSAGING_WRAPPED_REQUEST_KEY",""),local=e.getProperty("NIGSON_MESSAGING_LOCAL_REQUEST_KEY","");byte[] key;if(!local.isBlank())key=MessageKey.load(null,env,revision,arn,wrapped,local);else try(var kms=KmsClient.create()){key=MessageKey.load(kms,env,revision,arn,wrapped,local);}String configured=e.getRequiredProperty("NIGSON_MESSAGING_WHATSAPP_BINDINGS");if(configured.length()>16000)throw new IllegalArgumentException("MESSAGE_BINDINGS_TOO_LARGE");var json=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(configured);if(!json.isObject())throw new IllegalArgumentException("MESSAGE_BINDINGS_REQUIRED");var bindings=new HashMap<String,Map<String,String>>();for(var entry:json.properties()){if(!entry.getValue().isObject())throw new IllegalArgumentException("MESSAGE_BINDINGS_REQUIRED");var values=new HashMap<String,String>();for(var field:entry.getValue().properties()){if(!field.getValue().isString())throw new IllegalArgumentException("MESSAGE_BINDINGS_REQUIRED");values.put(field.getKey(),field.getValue().asString());}bindings.put(entry.getKey(),values);}return new VerificationMessage(env,e.getRequiredProperty("NIGSON_TWILIO_STATUS_PUBLIC_URL"),key,bindings);}
 @Bean VerificationWorker verificationWorker(VerificationExecution execution,VerificationContextClient source,VerificationMessage messages,TwilioAdapter provider,Environment e){enabled(e);return new VerificationWorker(execution,source,messages,provider,provider,Clock.systemUTC(),Instant.parse(e.getRequiredProperty("NIGSON_MESSAGING_APPROVAL_UNTIL")));}
}
