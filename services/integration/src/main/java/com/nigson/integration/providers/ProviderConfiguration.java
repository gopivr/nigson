package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_PROVIDERS_ENABLED",havingValue="true")
public class ProviderConfiguration {
 @Bean EmailAdapter smtp(@Value("${APP_ENV}") String environment,@Value("${EMAIL_MODE}") String mode,@Value("${SMTP_HOST}") String host,@Value("${SMTP_PORT}") int port,@Value("${SMTP_USERNAME:}") String username,@Value("${SMTP_PASSWORD:}") String password,@Value("${SMTP_FROM}") String from){return new EmailAdapter(environment,ProviderHttp.Mode.valueOf(mode),host,port,username,password,from);}
}
