package com.nigson.experience.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleRecaptchaAntiSpamVerifierTest {
    private static final URI ENDPOINT = URI.create("https://verify.example.test/siteverify");

    @Test
    void acceptsOnlySuccessfulHighScoreResponsesForExpectedActionAndHostname() throws Exception {
        var client = client(response(200, """
                {"success":true,"score":0.9,"action":"lead_submit","hostname":"www.example.com"}
                """));
        var verifier = new GoogleRecaptchaAntiSpamVerifier(
                "server-secret", Set.of("www.example.com"), "lead_submit", 0.5, client, ENDPOINT);

        assertThat(verifier.verify("google-token", "idempotency-key")).isTrue();

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(client).send(request.capture(), any());
        assertThat(request.getValue().uri()).isEqualTo(ENDPOINT);
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(3));
        assertThat(request.getValue().headers().firstValue("Content-Type"))
                .contains("application/x-www-form-urlencoded");
    }

    @Test
    void rejectsLowScoreWrongActionOrUnapprovedHostname() throws Exception {
        for (String result : Set.of(
                "{\"success\":true,\"score\":0.4,\"action\":\"lead_submit\",\"hostname\":\"www.example.com\"}",
                "{\"success\":true,\"score\":0.9,\"action\":\"other\",\"hostname\":\"www.example.com\"}",
                "{\"success\":true,\"score\":0.9,\"action\":\"lead_submit\",\"hostname\":\"attacker.example\"}")) {
            var verifier = new GoogleRecaptchaAntiSpamVerifier(
                    "server-secret", Set.of("www.example.com"), "lead_submit", 0.5,
                    client(response(200, result)), ENDPOINT);
            assertThat(verifier.verify("google-token", "idempotency-key")).isFalse();
        }
    }

    @Test
    void failsClosedWhenGoogleIsUnavailableOrReturnsInvalidJson() throws Exception {
        var unavailable = new GoogleRecaptchaAntiSpamVerifier(
                "server-secret", Set.of("www.example.com"), "lead_submit", 0.5,
                client(response(503, "{}")), ENDPOINT);
        assertThatThrownBy(() -> unavailable.verify("google-token", "idempotency-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RECAPTCHA_PROVIDER_UNAVAILABLE");

        var invalid = new GoogleRecaptchaAntiSpamVerifier(
                "server-secret", Set.of("www.example.com"), "lead_submit", 0.5,
                client(response(200, "not-json")), ENDPOINT);
        assertThatThrownBy(() -> invalid.verify("google-token", "idempotency-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RECAPTCHA_PROVIDER_UNAVAILABLE");
    }

    @Test
    void rejectsInvalidTokenWithoutCallingGoogle() throws Exception {
        var http = mock(HttpClient.class);
        var verifier = new GoogleRecaptchaAntiSpamVerifier(
                "server-secret", Set.of("www.example.com"), "lead_submit", 0.5, http, ENDPOINT);

        assertThat(verifier.verify(" ", "idempotency-key")).isFalse();
        org.mockito.Mockito.verifyNoInteractions(http);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new GoogleRecaptchaAntiSpamVerifier(
                "", Set.of("www.example.com"), "lead_submit", 0.5,
                mock(HttpClient.class), ENDPOINT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LEAD_RECAPTCHA_CONFIGURATION_INVALID");
    }

    private static HttpClient client(HttpResponse<byte[]> response) throws Exception {
        var client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return client;
    }

    private static HttpResponse<byte[]> response(int status, String body) {
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
