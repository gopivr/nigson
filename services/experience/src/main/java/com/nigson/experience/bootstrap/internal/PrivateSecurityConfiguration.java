package com.nigson.experience.bootstrap.internal;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import com.nigson.shared.security.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public class PrivateSecurityConfiguration {
 @Bean PrivateWorkload privateTrust(@Value("${APP_ENV}")String env,@Value("${NIGSON_PRODUCT_PHASE:1}")int phase,@Value("${NIGSON_OIDC_ISSUER}")String issuer,@Value("${NIGSON_PRIVATE_PEER_CIDRS}")String cidrs,@Value("${NIGSON_ACTOR_PUBLIC_KEYS}")String keys){return new PrivateWorkload("experience",phase,new PrivateIngress(List.of(cidrs.split(","))),r->KeycloakIdentity.decoder(new KeycloakIdentity.Policy(env,issuer,issuer+"/protocol/openid-connect/certs","nigson-experience",Set.of(r.client()),Set.of(r.scope()))),ActorPublicKeys.parse(keys),Set.of(issuer),Clock.systemUTC());}
 @Bean SecurityFilterChain privateSecurity(HttpSecurity http,PrivateWorkload trust,com.nigson.experience.platform.ProblemWriter problems)throws Exception {
  return http.securityContext(c->c.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository())).sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).csrf(c->c.disable())
   .addFilterAfter(new PrivateProofFilter(trust,(r,s)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.AUTH_REQUIRED,List.of())),SecurityContextHolderFilter.class)
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/liveness","/actuator/health/readiness").permitAll().requestMatchers("/internal/**").authenticated().anyRequest().denyAll())
   .exceptionHandling(e->e.authenticationEntryPoint((r,s,f)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.AUTH_REQUIRED,List.of())).accessDeniedHandler((r,s,f)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.ACCESS_DENIED,List.of()))).build();
 }
}
