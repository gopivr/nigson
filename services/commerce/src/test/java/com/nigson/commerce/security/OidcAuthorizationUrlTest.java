package com.nigson.commerce.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The BFF only forwards closed, known Keycloak required actions. */
class OidcAuthorizationUrlTest {
    private static PendingLogin flow() {
        String value = "a".repeat(43);
        return new PendingLogin(1, value, value, value, "/account/settings", Instant.now().plusSeconds(120));
    }

    @Test
    void defaultAuthorizationCarriesNoRequiredAction() {
        String query = OidcTrust.authorizationUrl("https://identity.example/realms/nigson", "nigson-bff",
                "https://shop.example/bff/callback", flow(), false, null).getQuery();
        assertThat(query).contains("code_challenge_method=S256").doesNotContain("kc_action").doesNotContain("prompt=");
    }

    @Test
    void passwordActionAddsKeycloakUpdatePassword() {
        String query = OidcTrust.authorizationUrl("https://identity.example/realms/nigson", "nigson-bff",
                "https://shop.example/bff/callback", flow(), false, "UPDATE_PASSWORD").getQuery();
        assertThat(query).contains("kc_action=UPDATE_PASSWORD")
                .contains("state=" + "a".repeat(43))
                .contains("code_challenge_method=S256");
    }

    @Test
    void unknownActionsAreRejectedBeforeBuildingAUrl() {
        assertThatThrownBy(() -> OidcTrust.authorizationUrl("https://identity.example/realms/nigson", "nigson-bff",
                "https://shop.example/bff/callback", flow(), false, "DELETE_ACCOUNT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OIDC_ACTION_INVALID");
    }

    @Test
    void stepUpKeepsReauthenticationForSensitiveActions() {
        String query = OidcTrust.authorizationUrl("https://identity.example/realms/nigson", "nigson-bff",
                "https://shop.example/bff/callback", flow(), true, "UPDATE_PASSWORD").getQuery();
        assertThat(query).contains("max_age=0").contains("prompt=login").contains("kc_action=UPDATE_PASSWORD");
    }
}
