package com.nigson.integration.bootstrap.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
public class FoundationConfiguration {
    public FoundationConfiguration(@Value("${APP_ENV}") String environment) {
        if (!java.util.Set.of("local", "test").contains(environment)) {
            throw new IllegalArgumentException("Foundation-only runtime requires APP_ENV=local|test");
        }
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="false",matchIfMissing=true)
    SecurityFilterChain foundationSecurity(HttpSecurity http,com.nigson.integration.platform.ProblemWriter problems) throws Exception {
        // No permissive developer bypass. Identity/session/domain authorization is SEC-001.
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .anyRequest().denyAll()).exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,failure)->problems.write(request,response,com.nigson.shared.problem.ProblemCode.AUTH_REQUIRED,java.util.List.of())).accessDeniedHandler((request,response,failure)->problems.write(request,response,com.nigson.shared.problem.ProblemCode.ACCESS_DENIED,java.util.List.of())))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}).authenticationEntryPoint((request,response,failure)->problems.write(request,response,com.nigson.shared.problem.ProblemCode.AUTH_REQUIRED,java.util.List.of()))).build();
    }

    @Bean
    JwtDecoder foundationDecoder(@Value("${APP_ENV}") String environment,
                                 @Value("${NIGSON_OIDC_ISSUER}") String issuer,
                                 @Value("${NIGSON_OIDC_JWKS}") String jwks,
                                 @Value("${NIGSON_OIDC_AUDIENCE:nigson-integration}") String audience,
                                 @Value("${NIGSON_OIDC_AUTHORIZED_CLIENTS:nigson-bff,nigson-local-fixtures}") String clients,
                                 @Value("${NIGSON_OIDC_REQUIRED_SCOPES:openid}") String scopes) {
        return com.nigson.shared.security.KeycloakIdentity.decoder(new com.nigson.shared.security.KeycloakIdentity.Policy(
            environment,issuer,jwks,audience,java.util.Set.of(clients.split(",")),java.util.Set.of(scopes.split(","))));
    }

    @Bean
    HealthIndicator ownerSchema(JdbcTemplate jdbc) {
        return () -> {
            try {
                Boolean initialized = jdbc.queryForObject(
                        "SELECT current_user = 'nigson_integration_app' AND current_schema() = 'integration' "
                        + "AND EXISTS (SELECT 1 FROM integration.databasechangelog WHERE id = 'integration-20261005-001')",
                        Boolean.class);
                return Boolean.TRUE.equals(initialized) ? Health.up().build() : Health.down().build();
            } catch (org.springframework.dao.DataAccessException failure) {
                return Health.down().build();
            }
        };
    }
}
