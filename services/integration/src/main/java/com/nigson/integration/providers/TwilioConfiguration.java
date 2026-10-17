package com.nigson.integration.providers;
import com.nigson.shared.security.ProviderHttp;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
/** Messaging does not require unrelated carrier or email credentials. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${NIGSON_PROVIDERS_ENABLED:false} or ${NIGSON_MESSAGING_VERIFICATION_WORKER_ENABLED:false} or ${NIGSON_MESSAGING_STAFF_REPLY_ENABLED:false}")
public class TwilioConfiguration {
 @Bean TwilioAdapter twilio(@Value("${APP_ENV}") String environment,@Value("${TWILIO_MODE}") String mode,@Value("${TWILIO_BASE_URL}") String endpoint,@Value("${TWILIO_ACCOUNT_SID}") String account,@Value("${TWILIO_AUTH_TOKEN}") String token,@Value("${TWILIO_MESSAGING_SERVICE_SID}") String service){return new TwilioAdapter(environment,ProviderHttp.Mode.valueOf(mode),endpoint,account,token,service);}
}
