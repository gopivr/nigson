package com.nigson.commerce.security;

import java.time.*;
import java.util.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.interfaces.ECPublicKey;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import com.nigson.shared.security.*;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.StandardRetryStrategy;

/** Explicit activation requires real security inputs. No production key/mock/client defaults. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class SecurityConfiguration {
    @Bean KmsClient identityKms(@Value("${APP_ENV}")String env,@Value("${NIGSON_AWS_REGION}")String region,@Value("${NIGSON_KMS_LOCAL_ENDPOINT:}")String endpoint){
        var builder=KmsClient.builder().region(software.amazon.awssdk.regions.Region.of(region)).overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(5)).apiCallAttemptTimeout(Duration.ofSeconds(3)).retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build());
        if(Set.of("local","test").contains(env)){var uri=java.net.URI.create(endpoint);if(!"http".equals(uri.getScheme())||!Set.of("127.0.0.1","localhost","localstack").contains(uri.getHost())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("LOCAL_KMS_TARGET_REQUIRED");builder.endpointOverride(uri).credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("mock-local-kms","mock-local-kms-secret")));}
        else{if(!Set.of("dev","qa","stage","prod").contains(env)||!endpoint.isBlank())throw new IllegalArgumentException("PRODUCTION_KMS_TARGET_REQUIRED");builder.credentialsProvider(software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider.builder().build());}return builder.build();
    }
    @Bean SessionCodec sessionCodec(KmsClient kms,@Value("${APP_ENV}")String env,@Value("${NIGSON_SESSION_KEY_REVISION}")String revision,@Value("${NIGSON_SESSION_KMS_KEY:}")String arn,@Value("${NIGSON_SESSION_WRAPPED_KEY:}")String wrapped,@Value("${NIGSON_SESSION_LOCAL_KEY:}")String local,@Value("${NIGSON_SESSION_PREVIOUS_WRAPPED_KEYS:{}}")String previous){
        if(!local.isBlank()){if(!Set.of("local","test").contains(env)||!arn.isBlank()||!wrapped.isBlank())throw new IllegalArgumentException("LOCAL_SESSION_KEY_FORBIDDEN");return new SessionCodec(revision,Map.of(revision,Base64.getDecoder().decode(local)));}
        if(!arn.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}"))throw new IllegalArgumentException("SESSION_KMS_KEY_REQUIRED");
        var keys=new HashMap<String,byte[]>();keys.put(revision,IdentityKeys.unwrap(kms,arn,wrapped,revision));
        var prior=new tools.jackson.databind.json.JsonMapper().readTree(previous);if(!prior.isObject()||prior.size()>2)throw new IllegalArgumentException("SESSION_KEY_OVERLAP_INVALID");for(var entry:prior.properties()){if(!entry.getValue().isString()||keys.containsKey(entry.getKey()))throw new IllegalArgumentException("SESSION_KEY_OVERLAP_INVALID");keys.put(entry.getKey(),IdentityKeys.unwrap(kms,arn,entry.getValue().asString(),entry.getKey()));}return new SessionCodec(revision,keys);
    }
    @Bean(name="springSessionConversionService")org.springframework.core.convert.ConversionService typedSessionConversion(SessionCodec codec){return codec.conversionService();}
    @Bean BrowserBoundary browserBoundary(@Value("${APP_ENV}")String env,@Value("${NIGSON_BFF_ALLOWED_ORIGINS}")String origins){return new BrowserBoundary(env,List.of(origins.split(",")));}
    @Bean SessionCookies sessionCookies(@Value("${APP_ENV}")String env,@Value("${NIGSON_INSECURE_LOCAL_COOKIE:false}")boolean insecure){return new SessionCookies(env,insecure);}
    @Bean org.springframework.session.web.http.CookieSerializer cookieSerializer(SessionCookies cookies){return cookies.serializer();}
    @Bean JdbcSessionTrust sessionTrust(JdbcTemplate jdbc,PlatformTransactionManager manager,SessionCodec codec,@Value("${NIGSON_BFF_RETURN_PATHS}")String paths){return new JdbcSessionTrust(jdbc,new TransactionTemplate(manager),codec,Clock.systemUTC(),Set.of(paths.split(",")));}
    @Bean CommerceAuthority commerceAuthority(JdbcTemplate jdbc,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new CommerceAuthority(jdbc,Clock.systemUTC(),phase);}
    @Bean OidcTrust oidcTrust(@Value("${APP_ENV}")String env,@Value("${NIGSON_OIDC_ISSUER}")String issuer,@Value("${NIGSON_BFF_CLIENT_SECRET}")String secret,@Value("${NIGSON_BFF_CALLBACK}")String callback){return new OidcTrust(env,issuer,"nigson-bff",secret,callback,null,Clock.systemUTC());}
    @Bean SessionLifecycle sessionLifecycle(JdbcSessionTrust store,OidcTrust oidc,JdbcTemplate jdbc,io.micrometer.core.instrument.MeterRegistry metrics){return new SessionLifecycle(store,oidc,(issuer,subject)->{var parties=jdbc.query("SELECT id FROM commerce.party WHERE identity_issuer=? AND identity_subject=? AND status='ACTIVE'",(r,n)->r.getObject(1,UUID.class),issuer,subject);if(parties.size()!=1)throw ActorAssertion.denied();return parties.getFirst();},Clock.systemUTC(),metrics);}
    @Bean ActorDelegation actorDelegation(JdbcSessionTrust sessions,CommerceAuthority authority,IdentityKeys signer){return new ActorDelegation(sessions,authority,signer);}
    @Bean WorkloadCredentials commerceWorkload(@Value("${APP_ENV}")String env,@Value("${NIGSON_OIDC_ISSUER}")String issuer,@Value("${NIGSON_COMMERCE_WORKLOAD_SECRET}")String secret,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase){return new WorkloadCredentials(env,issuer,"nigson-commerce",secret,phase,null,Clock.systemUTC());}
    @Bean IdentityKeys actorSigner(KmsClient kms,@Value("${NIGSON_ACTOR_KMS_KEY}")String arn,@Value("${NIGSON_ACTOR_KEY_ID}")String kid,@Value("${NIGSON_ACTOR_PUBLIC_KEY}")String encoded){return new IdentityKeys(kms,arn,kid,publicKey(encoded),Clock.systemUTC());}
    @Bean PrivateWorkload commercePrivateTrust(@Value("${APP_ENV}")String env,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,@Value("${NIGSON_OIDC_ISSUER}")String issuer,@Value("${NIGSON_PRIVATE_PEER_CIDRS}")String cidrs,@Value("${NIGSON_ACTOR_PUBLIC_KEYS}")String keys){return new PrivateWorkload("commerce",phase,new PrivateIngress(List.of(cidrs.split(","))),r->KeycloakIdentity.decoder(new KeycloakIdentity.Policy(env,issuer,issuer+"/protocol/openid-connect/certs","nigson-commerce",Set.of(r.client()),Set.of(r.scope()))),ActorPublicKeys.parse(keys),Set.of(issuer),Clock.systemUTC());}
    @Bean SecurityFilterChain trustedSecurity(HttpSecurity http,PrivateWorkload trust,JdbcSessionTrust sessions,SessionCookies cookies,CommerceAuthority authority,com.nigson.commerce.platform.ProblemWriter problems,BrowserBoundary browser,@Value("${APP_ENV}")String env)throws Exception {
        return http.securityContext(c->c.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository())).sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .addFilterAfter(new TrustFilter(trust,sessions,cookies,authority,problems,browser),SecurityContextHolderFilter.class)
            .csrf(c->c.disable()).cors(c->c.disable())
            .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/liveness","/actuator/health/readiness").permitAll().requestMatchers(org.springframework.http.HttpMethod.OPTIONS,"/bff/**","/api/v1/**").permitAll().requestMatchers("/internal/**").authenticated().anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,f)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.AUTH_REQUIRED,List.of())).accessDeniedHandler((r,s,f)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.ACCESS_DENIED,List.of())))
            .headers(h->h.cacheControl(c->{}).contentTypeOptions(c->{}).httpStrictTransportSecurity(c->c.requestMatcher(r->!Set.of("local","test").contains(env)||r.isSecure()).maxAgeInSeconds(31536000).includeSubDomains(true))).build();
    }
    public static ECPublicKey publicKey(String base64){try{return (ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));}catch(Exception e){throw new IllegalArgumentException("ACTOR_PUBLIC_KEY_REQUIRED");}}
}
