package com.nigson.experience.lead;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;

final class GoogleRecaptchaAntiSpamVerifier implements LeadService.AntiSpamVerifier {
    private static final URI VERIFY_URI = URI.create("https://www.google.com/recaptcha/api/siteverify");
    private final String secret;
    private final Set<String> hostnames;
    private final String action;
    private final double minimumScore;
    private final HttpClient http;
    private final URI verifyUri;

    GoogleRecaptchaAntiSpamVerifier(String secret, Set<String> hostnames, String action, double minimumScore) {
        this(secret, hostnames, action, minimumScore,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build(),
                VERIFY_URI);
    }

    GoogleRecaptchaAntiSpamVerifier(String secret, Set<String> hostnames, String action, double minimumScore,
                                    HttpClient http, URI verifyUri) {
        if (secret == null || secret.isBlank() || secret.length() > 4096 || hostnames == null || hostnames.isEmpty()
                || hostnames.stream().anyMatch(host -> host == null || host.isBlank() || host.length() > 253)
                || action == null || !action.matches("[A-Za-z][A-Za-z0-9/_]{0,99}")
                || !Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1
                || http == null || verifyUri == null || !"https".equalsIgnoreCase(verifyUri.getScheme())) {
            throw new IllegalArgumentException("LEAD_RECAPTCHA_CONFIGURATION_INVALID");
        }
        this.secret = secret;
        this.hostnames = hostnames.stream().map(host -> host.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.action = action;
        this.minimumScore = minimumScore;
        this.http = http;
        this.verifyUri = verifyUri;
    }

    static GoogleRecaptchaAntiSpamVerifier from(Environment env) {
        Set<String> hostnames = java.util.Arrays.stream(env.getRequiredProperty("NIGSON_LEADS_RECAPTCHA_HOSTNAMES").split(","))
                .map(String::strip).filter(host -> !host.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new GoogleRecaptchaAntiSpamVerifier(
                env.getRequiredProperty("NIGSON_LEADS_RECAPTCHA_SECRET"), hostnames,
                env.getProperty("NIGSON_LEADS_RECAPTCHA_ACTION", "lead_submit"),
                env.getProperty("NIGSON_LEADS_RECAPTCHA_MIN_SCORE", Double.class, 0.5));
    }

    @Override
    public boolean verify(String token, String idempotencyKey) {
        if (token == null || token.isBlank() || token.length() > 4096
                || token.chars().anyMatch(c -> c < 32 || c == 127)) {
            return false;
        }
        String body = "secret=" + encode(secret) + "&response=" + encode(token);
        HttpRequest request = HttpRequest.newBuilder(verifyUri).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RECAPTCHA_PROVIDER_UNAVAILABLE");
        } catch (IOException failure) {
            throw new IllegalStateException("RECAPTCHA_PROVIDER_UNAVAILABLE");
        }
        if (response.statusCode() != 200 || response.body() == null || response.body().length > 8192) {
            throw new IllegalStateException("RECAPTCHA_PROVIDER_UNAVAILABLE");
        }
        JsonNode result;
        try {
            result = com.nigson.shared.events.EventCodec.JSON.readTree(response.body());
        } catch (RuntimeException invalidResponse) {
            throw new IllegalStateException("RECAPTCHA_PROVIDER_UNAVAILABLE");
        }
        JsonNode score = result.get("score");
        String hostname = result.path("hostname").asString("").toLowerCase(Locale.ROOT);
        return result.path("success").asBoolean(false)
                && score != null && score.isNumber() && score.asDouble() >= minimumScore
                && action.equals(result.path("action").asString(""))
                && hostnames.contains(hostname);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "GoogleRecaptchaAntiSpamVerifier[protected]";
    }
}
