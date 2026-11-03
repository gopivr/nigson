package com.nigson.commerce.session;

import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.problem.ProblemCode;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.events.EventCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Local/test-only public signup adapter. Passwords are sent once to Keycloak and are never persisted here. */
public final class RegistrationService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,190}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .’'-]{0,79}$");
    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder(
        tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(6).maxStringLength(8192).maxNumberLength(20).build()).build())
        .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final String backchannel, adminBase, realm, clientId, clientSecret;
    private final HttpClient http;
    private final Map<String, Window> attempts = new ConcurrentHashMap<>();
    private record Window(long startedAt, AtomicInteger count) {}
    public record Input(String firstName, String lastName, String email, String password) {
        @Override public String toString() { return "Input[protected]"; }
    }

    public RegistrationService(String environment, String issuer, String clientId, String clientSecret) {
        this(environment, issuer, issuer + "/protocol/openid-connect/certs", clientId, clientSecret);
    }

    public RegistrationService(String environment, String issuer, String jwks, String clientId, String clientSecret) {
        this(environment, issuer, clientId, clientSecret, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build(), jwks);
    }

    RegistrationService(String environment, String issuer, String clientId, String clientSecret, HttpClient http) {
        this(environment, issuer, clientId, clientSecret, http, issuer + "/protocol/openid-connect/certs");
    }

    private RegistrationService(String environment, String issuer, String clientId, String clientSecret, HttpClient http, String jwks) {
        URI target = URI.create(issuer), keys = URI.create(jwks); String suffix = "/protocol/openid-connect/certs";
        if (!Set.of("local", "test").contains(environment) || !"http".equals(target.getScheme())
            || !Set.of("localhost", "127.0.0.1").contains(target.getHost()) || target.getQuery() != null
            || target.getFragment() != null || !target.getPath().matches("/realms/[A-Za-z0-9._-]{1,64}")
            || !"http".equals(keys.getScheme()) || !Set.of("localhost", "127.0.0.1", "keycloak").contains(keys.getHost())
            || keys.getQuery() != null || keys.getFragment() != null || !keys.getPath().equals(target.getPath() + suffix)
            || clientId == null || !clientId.equals("nigson-registration") || clientSecret == null
            || clientSecret.length() < 32 || clientSecret.length() > 512) {
            throw new IllegalArgumentException("LOCAL_REGISTRATION_CONFIG_REQUIRED");
        }
        this.backchannel = jwks.substring(0, jwks.length() - suffix.length()); this.adminBase = keys.getScheme() + "://" + keys.getAuthority();
        this.realm = target.getPath().substring("/realms/".length());
        this.clientId = clientId; this.clientSecret = clientSecret; this.http = http;
    }

    /** Returns the same accepted response for a newly created or already existing address. */
    public void register(Input input, String remoteAddress) {
        Input checked = validate(input);
        limit(remoteAddress);
        String token = token();
        var user = new LinkedHashMap<String,Object>();
        user.put("username", checked.email().toLowerCase(java.util.Locale.ROOT));
        user.put("email", checked.email().toLowerCase(java.util.Locale.ROOT));
        user.put("firstName", checked.firstName()); user.put("lastName", checked.lastName());
        user.put("enabled", true); user.put("emailVerified", false);
        user.put("credentials", java.util.List.of(Map.of("type", "password", "value", checked.password(), "temporary", false)));
        HttpResponse<java.io.InputStream> response = send("/admin/realms/" + realm + "/users", "POST", token,
            JSON.writeValueAsBytes(user), "application/json");
        try (var ignored = response.body()) {
            if (response.statusCode() == 201 || response.statusCode() == 409) return;
            if (response.statusCode() == 400) throw ApiValues.invalid();
            throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
        } catch (java.io.IOException failure) { throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE); }
    }

    static Input validate(Input input) {
        if (input == null || input.firstName() == null || input.lastName() == null || input.email() == null || input.password() == null
            || !NAME.matcher(input.firstName().trim()).matches() || !NAME.matcher(input.lastName().trim()).matches()
            || !EMAIL.matcher(input.email().trim()).matches() || input.email().length() > 254
            || input.password().length() < 12 || input.password().length() > 128
            || input.password().codePoints().anyMatch(Character::isISOControl)) throw ApiValues.invalid();
        return new Input(input.firstName().trim(), input.lastName().trim(), input.email().trim(), input.password());
    }

    private String token() {
        byte[] body = ("grant_type=client_credentials&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)).getBytes(StandardCharsets.UTF_8);
        HttpResponse<java.io.InputStream> response = send("/protocol/openid-connect/token", "POST", null, body, "application/x-www-form-urlencoded");
        try {
            if (response.statusCode() != 200) throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
            byte[] bytes; try (var stream = response.body()) { bytes = stream.readNBytes(16385); }
            if (bytes.length > 16384) throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
            var node = JSON.readTree(bytes);
            if (!node.isObject() || !node.path("access_token").isString() || !"Bearer".equals(node.path("token_type").asString())) throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
            String token = node.path("access_token").asString();
            if (token.isBlank() || token.length() > 12000) throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
            return token;
        } catch (Problem.Failure safe) { throw safe; }
        catch (Exception failure) { throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE); }
    }

    private HttpResponse<java.io.InputStream> send(String path, String method, String token, byte[] body, String contentType) {
        try {
            String root = path.startsWith("/admin/") ? adminBase : backchannel;
            var request = HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json").header("Content-Type", contentType);
            if (token != null) request.header("Authorization", "Bearer " + token);
            return http.send(request.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);
        }
    }
    private void limit(String remote) {
        if (remote == null || remote.length() > 128 || remote.codePoints().anyMatch(Character::isISOControl)) throw ApiValues.invalid();
        long now = System.currentTimeMillis(); String key = EventCodec.hash(EventCodec.JSON.valueToTree(remote));
        if (attempts.size() >= 4096) attempts.entrySet().removeIf(e -> now - e.getValue().startedAt() >= 60_000);
        if (attempts.size() >= 4096) throw new Problem.Failure(ProblemCode.RATE_LIMITED);
        Window current = attempts.compute(key, (ignored, prior) -> prior == null || now - prior.startedAt() >= 60_000
            ? new Window(now, new AtomicInteger()) : prior);
        if (current.count().incrementAndGet() > 5) throw new Problem.Failure(ProblemCode.RATE_LIMITED);
    }
    private static String encode(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
