package com.nigson.commerce.files;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.nigson.commerce.platform.*;
import com.nigson.shared.problem.*;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.StandardRetryStrategy;

@Configuration(proxyBeanMethods=false)
public class FileConfiguration {
    @Bean FileRepository fileRepository(JdbcTemplate jdbc){return new JdbcFileRepository(jdbc,Clock.systemUTC());}
    private String required(Environment env,String key){String value=env.getProperty(key,"");if(value.isBlank())throw new IllegalArgumentException("FILE_STORAGE_CONFIGURATION_REQUIRED");return value;}
    @Bean(destroyMethod="close") public FileStorage fileStorage(Environment config){
        String enabled=config.getProperty("NIGSON_FILES_ENABLED","false");if(!Set.of("true","false").contains(enabled))throw new IllegalArgumentException("FILE_STORAGE_CONFIGURATION_INVALID");
        if(enabled.equals("false"))return new FileStorage(){public boolean configured(){return false;}private Problem.Failure disabled(){return new Problem.Failure(ProblemCode.FEATURE_DISABLED);}public Signed upload(UUID id,String key,FileValues.Upload input,String owner,Instant time){throw disabled();}public ObjectInfo quarantine(String key){throw disabled();}public ObjectInfo finalObject(String key,boolean secret){throw disabled();}public void promote(String source,String version,String etag,String target,boolean secret){throw disabled();}public Signed download(String key,boolean secret,Instant time){throw disabled();}};
        String env=required(config,"APP_ENV"),region=required(config,"NIGSON_AWS_REGION");if(!Set.of("local","test","dev","qa","stage","prod").contains(env)||!region.matches("[a-z]{2}(?:-gov)?-[a-z]+-[0-9]"))throw new IllegalArgumentException("FILE_STORAGE_CONFIGURATION_INVALID");
        String quarantine=required(config,"NIGSON_S3_QUARANTINE_BUCKET"),publicBucket=required(config,"NIGSON_S3_PUBLIC_BUCKET"),privateBucket=required(config,"NIGSON_S3_PRIVATE_BUCKET");
        for(String bucket:List.of(quarantine,publicBucket,privateBucket))if(!bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")||bucket.matches("[0-9-]+"))throw new IllegalArgumentException("FILE_STORAGE_BUCKET_INVALID");if(new HashSet<>(List.of(quarantine,publicBucket,privateBucket)).size()!=3)throw new IllegalArgumentException("FILE_STORAGE_BUCKET_INVALID");
        String endpoint=config.getProperty("NIGSON_S3_LOCAL_ENDPOINT",""),ca=config.getProperty("NIGSON_S3_LOCAL_CA",""),kms=config.getProperty("NIGSON_S3_KMS_KEY","");boolean local=Set.of("local","test").contains(env);
        var http=ApacheHttpClient.builder().connectionTimeout(Duration.ofSeconds(1)).socketTimeout(Duration.ofSeconds(3)).maxConnections(20).connectionAcquisitionTimeout(Duration.ofSeconds(1));
        var client=S3Client.builder().region(Region.of(region)).overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(5)).apiCallAttemptTimeout(Duration.ofSeconds(4)).retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build());
        var signer=S3Presigner.builder().region(Region.of(region));
        if(local){
            URI uri;try{uri=URI.create(endpoint);}catch(IllegalArgumentException bad){throw new IllegalArgumentException("LOCAL_S3_TARGET_REQUIRED");}
            if(!"https".equals(uri.getScheme())||!Set.of("127.0.0.1","localhost","s3-mock").contains(uri.getHost())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath())||!kms.isBlank())throw new IllegalArgumentException("LOCAL_S3_TARGET_REQUIRED");
            var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create("mock-local-s3","mock-local-s3-secret"));client.endpointOverride(uri).credentialsProvider(credentials).forcePathStyle(true);signer.endpointOverride(uri).credentialsProvider(credentials).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).checksumValidationEnabled(false).build());
            if(!ca.isBlank())try{var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);try(var input=Files.newInputStream(Path.of(ca))){store.setCertificateEntry("local-s3-ca",CertificateFactory.getInstance("X.509").generateCertificate(input));}var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);http.tlsTrustManagersProvider(factory::getTrustManagers);}catch(Exception invalid){throw new IllegalArgumentException("LOCAL_S3_CA_INVALID");}
        }else{
            if(!endpoint.isBlank()||!ca.isBlank()||!kms.matches("arn:aws(?:-us-gov|-cn)?:kms:"+region+":[0-9]{12}:key/[a-f0-9-]{36}"))throw new IllegalArgumentException("PRODUCTION_S3_CONFIGURATION_REQUIRED");
            var credentials=ContainerCredentialsProvider.builder().build();client.credentialsProvider(credentials);signer.credentialsProvider(credentials);
        }
        return new S3FileStorage(client.httpClientBuilder(http).build(),signer.build(),quarantine,publicBucket,privateBucket,kms,Clock.systemUTC());
    }
    @Bean FileService fileService(FileRepository repository,FileStorage storage,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager,Environment env,MeterRegistry metrics){return new FileService(repository,storage,commands,audit,manager,Clock.systemUTC(),env.getRequiredProperty("APP_ENV"),metrics);}
}
