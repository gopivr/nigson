package com.nigson.integration.notification;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import com.nigson.integration.platform.AuditWriter;
import com.nigson.shared.security.ProviderHttp;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_MESSAGING_VERIFICATION_INTAKE_ENABLED",havingValue="true")
public class NotificationIntakeConfiguration {
 @Bean VerificationRequestedHandler verificationRequestedHandler(Environment e,AuditWriter audit){if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("MESSAGE_PRIVATE_SECURITY_REQUIRED");return new VerificationRequestedHandler(audit,Clock.systemUTC(),e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(e.getRequiredProperty("TWILIO_MODE")),e.getRequiredProperty("NIGSON_MESSAGING_LOCALE"),Map.of("SMS",e.getRequiredProperty("NIGSON_MESSAGING_VERIFICATION_SMS_REVISION",Integer.class),"WHATSAPP",e.getRequiredProperty("NIGSON_MESSAGING_VERIFICATION_WHATSAPP_REVISION",Integer.class)),e.getRequiredProperty("TWILIO_ACCOUNT_SID"),e.getRequiredProperty("TWILIO_MESSAGING_SERVICE_SID"),com.nigson.shared.problem.ApiValues.uuid(e.getRequiredProperty("NIGSON_MESSAGING_CREATION_GENERATION")));}
}
