package com.nigson.commerce.notification;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import com.nigson.commerce.security.WorkloadCredentials;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"NIGSON_SECURITY_ENABLED","NIGSON_MESSAGING_STATUS_ENABLED"},havingValue="true")
public class TwilioStatusConfiguration {
 @Bean TwilioStatusIngress twilioStatusIngress(Environment e,WorkloadCredentials credentials){return new TwilioStatusIngress(e.getRequiredProperty("APP_ENV"),e.getRequiredProperty("TWILIO_ACCOUNT_SID"),e.getRequiredProperty("TWILIO_AUTH_TOKEN"),e.getRequiredProperty("NIGSON_TWILIO_STATUS_PUBLIC_URL"),e.getRequiredProperty("NIGSON_INTEGRATION_BASE_URL"),credentials::token,Clock.systemUTC());}
}
