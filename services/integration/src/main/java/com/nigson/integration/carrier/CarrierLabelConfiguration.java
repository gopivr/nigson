package com.nigson.integration.carrier;

import java.net.URI;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"NIGSON_CARRIER_ENABLED","NIGSON_CARRIER_LABELS_ENABLED"},havingValue="true")
public class CarrierLabelConfiguration {
 @Bean(destroyMethod="close") public S3Client carrierLabelClient(Environment env){
  if(!env.getProperty("NIGSON_SECURITY_ENABLED",Boolean.class,false))throw new IllegalArgumentException("CARRIER_PRIVATE_SECURITY_REQUIRED");String environment=env.getRequiredProperty("APP_ENV"),region=env.getRequiredProperty("NIGSON_AWS_REGION"),endpoint=env.getProperty("NIGSON_S3_LOCAL_ENDPOINT",""),ca=env.getProperty("NIGSON_S3_LOCAL_CA",""),kms=env.getProperty("NIGSON_S3_KMS_KEY","");if(!Set.of("local","test","dev","qa","stage","prod").contains(environment)||!region.matches("[a-z]{2}(?:-gov)?-[a-z]+-[0-9]"))throw new IllegalArgumentException("CARRIER_PRIVATE_STORAGE_REQUIRED");var http=ApacheHttpClient.builder().connectionTimeout(Duration.ofSeconds(1)).socketTimeout(Duration.ofSeconds(3)).connectionAcquisitionTimeout(Duration.ofSeconds(1)).maxConnections(4);var builder=S3Client.builder().region(Region.of(region)).overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(5)).apiCallAttemptTimeout(Duration.ofSeconds(4)).retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build());
  if(Set.of("local","test").contains(environment)){URI uri;try{uri=URI.create(endpoint);}catch(RuntimeException invalid){throw new IllegalArgumentException("LOCAL_S3_TARGET_REQUIRED");}if(!"https".equals(uri.getScheme())||!Set.of("localhost","127.0.0.1","s3-mock").contains(uri.getHost())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath())||!kms.isEmpty())throw new IllegalArgumentException("LOCAL_S3_TARGET_REQUIRED");builder.endpointOverride(uri).forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("mock-local-s3","mock-local-s3-secret")));if(!ca.isEmpty())try{var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);try(var input=Files.newInputStream(Path.of(ca))){store.setCertificateEntry("local-s3-ca",CertificateFactory.getInstance("X.509").generateCertificate(input));}var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);http.tlsTrustManagersProvider(factory::getTrustManagers);}catch(Exception invalid){throw new IllegalArgumentException("LOCAL_S3_CA_INVALID");}}
  else{if(!endpoint.isEmpty()||!ca.isEmpty()||!kms.matches("arn:aws(?:-us-gov|-cn)?:kms:"+region+":[0-9]{12}:key/[a-f0-9-]{36}"))throw new IllegalArgumentException("PRODUCTION_S3_CONFIGURATION_REQUIRED");builder.credentialsProvider(ContainerCredentialsProvider.builder().build());}return builder.httpClientBuilder(http).build();
 }
 @Bean public CarrierLabelStorage carrierLabelStorage(S3Client carrierLabelClient,Environment env){return new CarrierLabelStorage(carrierLabelClient,env.getRequiredProperty("APP_ENV"),env.getRequiredProperty("NIGSON_S3_PRIVATE_BUCKET"),env.getProperty("NIGSON_S3_KMS_KEY",""));}
}
