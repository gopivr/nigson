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
   .addFilterAfter(new InboundBrowserHeaderFilter(),PrivateProofFilter.class)
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/liveness","/actuator/health/readiness").permitAll().requestMatchers("/api/v1/webhooks/notifications/twilio/whatsapp","/api/v1/career-opportunities","/api/v1/career-upload-sessions","/api/v1/newsletter-subscriptions","/api/v1/newsletter-subscriptions/confirmation","/api/v1/unsubscribe","/api/v1/content/**","/api/v1/content","/api/v1/seo/sitemap","/api/v1/channels").permitAll().requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/enquiries","/api/v1/career-opportunities/*/applications").permitAll().requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/reviews","/api/v1/campaigns").permitAll().requestMatchers("/internal/**","/api/v1/experience/files/**","/api/v1/me/consents","/api/v1/staff/leads","/api/v1/staff/leads/*","/api/v1/staff/content","/api/v1/staff/content/*/revisions","/api/v1/staff/content-revisions/*/review","/api/v1/staff/content-revisions/*/approval","/api/v1/staff/content-revisions/*/publication","/api/v1/staff/content-revisions/*/preview","/api/v1/staff/channel-configurations/*","/api/v1/staff/career-opportunities","/api/v1/staff/career-applications","/api/v1/staff/career-applications/*","/api/v1/staff/reviews","/api/v1/staff/reviews/*/moderation","/api/v1/staff/campaigns","/api/v1/staff/reports/engagement","/api/v1/experience/staff/audit-records","/api/v1/experience/staff/configuration/*","/api/v1/distributor-applications","/api/v1/me/distributor-applications","/api/v1/staff/distributor-applications","/api/v1/staff/distributor-applications/*","/api/v1/staff/distributor-applications/*/review","/api/v1/staff/distributor-applications/*/decision","/api/v1/quote-requests").authenticated().requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/reviews").authenticated().anyRequest().denyAll())
   .exceptionHandling(e->e.authenticationEntryPoint((r,s,f)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.AUTH_REQUIRED,List.of())).accessDeniedHandler((r,s,f)->problems.write(r,s,com.nigson.shared.problem.ProblemCode.ACCESS_DENIED,List.of()))).build();
 }
 private static final class InboundBrowserHeaderFilter extends org.springframework.web.filter.OncePerRequestFilter {
  @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException {
   if("/api/v1/webhooks/notifications/twilio/whatsapp".equals(request.getRequestURI())&&(request.getHeader("Origin")!=null||request.getHeader("Cookie")!=null)){response.setStatus(400);response.setHeader("Cache-Control","no-store");return;}chain.doFilter(request,response);
  }
 }
}
