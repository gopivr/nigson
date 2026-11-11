package com.nigson.integration.providers;

import com.nigson.shared.security.ProviderHttp;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

/** Explicit opt-in: missing partner/account data fails startup; no mock fallback. */
@Configuration(proxyBeanMethods=false)
public class PartnerConfiguration {
 @Bean GeocodingProvider geocodingProvider(Environment e){
  String provider=e.getProperty("NIGSON_GEOCODING_PROVIDER", "");
  String key=e.getProperty("GOOGLE_GEOCODING_API_KEY", "");
  if(provider.isBlank()||key.isBlank())return address->{throw new com.nigson.shared.problem.Problem.Failure(com.nigson.shared.problem.ProblemCode.GEOCODING_NOT_CONFIGURED);};
  if(!provider.equals("GOOGLE"))throw new IllegalArgumentException("GEOCODING_PROVIDER_UNSUPPORTED");
  return new GoogleGeocodingAdapter(e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.LIVE,"https://maps.googleapis.com",key);
 }

 @Bean @ConditionalOnProperty(name="NIGSON_PANDAGO_ENABLED",havingValue="true")
 PandagoAdapter pandago(Environment e) {
  if(!e.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("PANDAGO_PRIVATE_SECURITY_REQUIRED");
  try {
   var path=java.nio.file.Path.of(e.getRequiredProperty("PANDAGO_PRIVATE_KEY_FILE"));
   if(!java.nio.file.Files.isRegularFile(path)||java.nio.file.Files.size(path)>16384)throw new IllegalArgumentException("PANDAGO_PRIVATE_KEY_FILE_INVALID");
   return new PandagoAdapter(e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(e.getRequiredProperty("PANDAGO_MODE")),e.getRequiredProperty("PANDAGO_BASE_URL"),e.getRequiredProperty("PANDAGO_AUTH_BASE_URL"),e.getRequiredProperty("PANDAGO_CLIENT_ID"),e.getRequiredProperty("PANDAGO_KEY_ID"),e.getRequiredProperty("PANDAGO_SCOPE"),e.getRequiredProperty("PANDAGO_CLIENT_VENDOR_ID"),java.nio.file.Files.readString(path),java.time.Clock.systemUTC());
  }catch(java.io.IOException failure){throw new IllegalArgumentException("PANDAGO_PRIVATE_KEY_FILE_UNREADABLE");}
 }
 @Bean @ConditionalOnProperty(name="NIGSON_TERMII_ENABLED",havingValue="true")
 TermiiTokenAdapter termiiToken(Environment e){return new TermiiTokenAdapter(e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(e.getRequiredProperty("TERMII_MODE")),e.getRequiredProperty("TERMII_BASE_URL"),e.getRequiredProperty("TERMII_API_KEY"),e.getRequiredProperty("TERMII_SENDER_ID"));}
 @Bean @ConditionalOnProperty(name="NIGSON_DELLYMAN_ENABLED",havingValue="true")
 DellymanAdapter dellyman(Environment e){return new DellymanAdapter(e.getRequiredProperty("APP_ENV"),ProviderHttp.Mode.valueOf(e.getRequiredProperty("DELLYMAN_MODE")),e.getRequiredProperty("DELLYMAN_BASE_URL"),e.getRequiredProperty("DELLYMAN_API_KEY"),e.getRequiredProperty("DELLYMAN_WEBHOOK_SECRET"));}
}
