package com.nigson.commerce.bootstrap.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
public class FoundationConfiguration {
    public FoundationConfiguration(@Value("${APP_ENV}") String environment) {
        if (!java.util.Set.of("local", "test").contains(environment)) {
            throw new IllegalArgumentException("Foundation-only runtime requires APP_ENV=local|test");
        }
    }

    @Bean
    SecurityFilterChain foundationSecurity(HttpSecurity http) throws Exception {
        // No permissive developer bypass. Identity/session/domain authorization is SEC-001.
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .anyRequest().denyAll()).oauth2ResourceServer(resource -> resource.jwt(jwt -> {})).build();
    }

    @Bean
    JwtDecoder foundationDecoder(@Value("${NIGSON_OIDC_ISSUER}") String issuer,
                                 @Value("${NIGSON_OIDC_JWKS}") String jwks) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    HealthIndicator ownerSchema(JdbcTemplate jdbc) {
        return () -> {
            try {
                Boolean initialized = jdbc.queryForObject(
                        "SELECT current_user = 'nigson_commerce_app' AND current_schema() = 'commerce' "
                        + "AND (SELECT count(*) = 2 FROM commerce.databasechangelog WHERE id IN ('commerce-20261005-001', 'commerce-20261005-002')) "
                        + "AND to_regclass('commerce.spring_session') IS NOT NULL "
                        + "AND to_regclass('commerce.spring_session_attributes') IS NOT NULL",
                        Boolean.class);
                return Boolean.TRUE.equals(initialized) ? Health.up().build() : Health.down().build();
            } catch (org.springframework.dao.DataAccessException failure) {
                return Health.down().build();
            }
        };
    }
}
