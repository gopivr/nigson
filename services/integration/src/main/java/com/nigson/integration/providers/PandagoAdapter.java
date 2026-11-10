package com.nigson.integration.providers;

import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ProviderHttp;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Delivery Hero ODR 1.1.1, Nigeria. Owners persist intent before booking or cancellation. */
public final class PandagoAdapter {
    public record Location(String address, BigDecimal latitude, BigDecimal longitude) {
        public Location {
            text(address, 500);
            if (latitude == null || longitude == null || latitude.abs().compareTo(new BigDecimal("90")) > 0 || longitude.abs().compareTo(new BigDecimal("180")) > 0)
                throw new IllegalArgumentException("PANDAGO_COORDINATES_REQUIRED");
        }
        @Override public String toString() { return "PandagoLocation[protected]"; }
    }
    public record Recipient(String name, String phone, Location location) {
        public Recipient {
            text(name, 255);
            if (phone == null || !phone.matches("\\+234[1-9][0-9]{9}") || location == null) throw invalid();
        }
        @Override public String toString() { return "PandagoRecipient[protected]"; }
    }
    public record Quote(String id, BigDecimal fee, String currency, Instant expiresAt, String accountHash) {}
    public record Estimate(BigDecimal fee, String currency, Instant estimatedPickupTime, Instant estimatedDeliveryTime,
                           String deliveryDuration, String lowerBound, String upperBound) {}
    public record Order(String id, String clientOrderId, String status) {}
    public enum Cancellation { CANCELLED, LEGACY_ACKNOWLEDGED }
    private static final String AUDIENCE = "https://sts.deliveryhero.io";
    private static final String PREFIX = "/ng/api/v1";
    private static final Set<String> STATUSES = Set.of("NEW", "RECEIVED", "WAITING_FOR_TRANSPORT", "ASSIGNED_TO_TRANSPORT", "COURIER_ACCEPTED_DELIVERY", "NEAR_VENDOR", "PICKED_UP", "COURIER_LEFT_VENDOR", "NEAR_CUSTOMER", "DELIVERED", "DELAYED", "CANCELLED", "RETURNED_TO_VENDOR");
    private static final Set<String> REASONS = Set.of("ADDRESS_INCOMPLETE_MISSTATED", "BAD_WEATHER", "CLOSED", "COURIER_ACCIDENT", "COURIER_UNREACHABLE", "DELIVERY_ETA_TOO_LONG", "DUPLICATE_ORDER", "FOOD_QUALITY_SPILLAGE", "ITEM_UNAVAILABLE", "LATE_DELIVERY", "MISTAKE_ERROR", "NO_COURIER", "OUTSIDE_DELIVERY_AREA", "OUTSIDE_SERVICE_HOURS", "REASON_UNKNOWN", "TECHNICAL_PROBLEM", "TOO_BUSY", "UNABLE_TO_FIND", "UNABLE_TO_PAY", "UNREACHABLE", "WRONG_ORDER_ITEMS_DELIVERED");
    private final ProviderHttp api, auth;
    private final ProviderHttp.Mode mode;
    private final String clientId, keyId, scope, vendorId, accountHash;
    private final PrivateKey privateKey;
    private final Clock clock;
    private String accessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public PandagoAdapter(String environment, ProviderHttp.Mode mode, String apiBase, String authBase, String clientId, String keyId, String scope, String vendorId, String pem, Clock clock) {
        if (mode == null || clock == null || !"pandago.api.ng.*".equals(scope)) throw invalid();
        this.clientId = identifier(clientId); this.keyId = identifier(keyId); this.vendorId = identifier(vendorId);
        if (mode == ProviderHttp.Mode.TEST && (!"https://api-infra-eu-central-1.stg.ondemandrider.net".equals(apiBase) || !"https://sts-st.deliveryhero.io".equals(authBase)) ||
            mode == ProviderHttp.Mode.LIVE && (!"https://ondemand-api-glovoapp.deliveryhero.io".equals(apiBase) || !"https://sts.deliveryhero.io".equals(authBase))) throw new IllegalArgumentException("PANDAGO_MODE_TARGET_MISMATCH");
        api = new ProviderHttp(environment, mode, apiBase, "pandago-mock", Set.of("api-infra-eu-central-1.stg.ondemandrider.net", "ondemand-api-glovoapp.deliveryhero.io"), clientId, keyId, vendorId);
        auth = new ProviderHttp(environment, mode, authBase, "pandago-mock", Set.of("sts-st.deliveryhero.io", "sts.deliveryhero.io"), clientId, keyId);
        this.mode = mode; this.scope = scope; this.clock = clock;
        try {
            if (pem == null || pem.length() > 16384 || !pem.strip().startsWith("-----BEGIN PRIVATE KEY-----") || !pem.strip().endsWith("-----END PRIVATE KEY-----")) throw invalid();
            byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (((RSAPrivateKey) privateKey).getModulus().bitLength() < 2048) throw invalid();
        } catch (GeneralSecurityException | RuntimeException failure) { throw new IllegalArgumentException("PANDAGO_PRIVATE_KEY_INVALID"); }
        accountHash = EventCodec.hash(EventCodec.JSON.valueToTree(Map.of("provider", "PANDAGO", "mode", mode.name(), "clientId", clientId, "vendorId", vendorId, "scope", scope)));
    }
    public ProviderHttp.Mode mode() { return mode; }
    public String accountFingerprint() { return accountHash; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("PANDAGO_REQUEST_INVALID"); }
    private static String text(String value, int limit) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > limit || value.chars().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }
    private static String identifier(String value) { if (!text(value, 160).matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,159}")) throw invalid(); return value; }
    private static void outsideTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION"); }
    private static String encoded(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String assertion() {
        var now = clock.instant();
        String header = encoded(EventCodec.JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT", "kid", keyId)));
        String claims = encoded(EventCodec.JSON.writeValueAsBytes(Map.of("iss", clientId, "sub", clientId, "aud", AUDIENCE, "jti", UUID.randomUUID().toString(), "iat", now.getEpochSecond(), "exp", now.plusSeconds(60).getEpochSecond())));
        String signingInput = header + "." + claims;
        try { var signer = Signature.getInstance("SHA256withRSA"); signer.initSign(privateKey); signer.update(signingInput.getBytes(StandardCharsets.US_ASCII)); return signingInput + "." + encoded(signer.sign()); }
        catch (GeneralSecurityException failure) { throw new IllegalStateException("PANDAGO_ASSERTION_FAILED"); }
    }
    private synchronized String token() {
        outsideTransaction();
        if (accessToken != null && tokenExpiry.isAfter(clock.instant().plusSeconds(30))) return accessToken;
        var response = auth.call("POST", "/oauth2/token", "application/x-www-form-urlencoded", ProviderHttp.form(Map.of("grant_type", "client_credentials", "client_id", clientId, "scope", scope, "client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", "client_assertion", assertion())), null, false);
        try {
            var body = EventCodec.JSON.readTree(response.body());
            String value = body.path("access_token").asString();
            if (response.status() != 200 || !value.matches("[A-Za-z0-9._~+/-]{1,16384}=*") || !body.path("expires_in").isIntegralNumber()) throw invalid();
            long lifetime = body.path("expires_in").asLong(); if (lifetime < 1 || lifetime > 86400) throw invalid();
            tokenExpiry = clock.instant().plusSeconds(lifetime); accessToken = value; return value;
        } catch (RuntimeException failure) { accessToken = null; tokenExpiry = Instant.EPOCH; throw new ProviderHttp.Failure("PANDAGO_TOKEN_INVALID", false, response.status()); }
    }
    private ProviderHttp.Result call(String method, String path, Object body, boolean sideEffect) {
        outsideTransaction();
        String authorization = "Bearer " + token();
        try { return api.call(method, PREFIX + path, "application/json", body == null ? "" : EventCodec.JSON.writeValueAsString(body), authorization, sideEffect); }
        catch (ProviderHttp.Failure failure) {
            if (failure.status == 401) { synchronized (this) { accessToken = null; tokenExpiry = Instant.EPOCH; } }
            // A duplicate reference can identify an already accepted booking. Never blindly recreate it.
            if (sideEffect && method.equals("POST") && failure.status == 409) throw new ProviderHttp.Failure("PANDAGO_BOOKING_REQUIRES_RECONCILIATION", true, 409);
            throw failure;
        }
    }
    private static JsonNode object(ProviderHttp.Result response, boolean uncertain) {
        try { var body = EventCodec.JSON.readTree(response.body()); if (!body.isObject()) throw invalid(); return body; }
        catch (RuntimeException failure) { throw new ProviderHttp.Failure("PANDAGO_RESPONSE_INVALID", uncertain, response.status()); }
    }
    private static Map<String, Object> location(Location location) { return Map.of("address", location.address(), "latitude", location.latitude(), "longitude", location.longitude()); }
    /** Read-only account qualification; never creates or modifies the registered outlet. */
    public JsonNode outlet() {
        var response = call("GET", "/outlets/" + vendorId, null, false); var body = object(response, false);
        if (response.status() != 200 || !vendorId.equals(body.path("client_vendor_id").asString()) || !"NGN".equals(body.path("currency").asString())) throw new ProviderHttp.Failure("PANDAGO_OUTLET_MISMATCH", false, response.status());
        return body;
    }
    /** Quotes reserve a price for ten minutes; they do not create delivery orders. */
    public Quote quote(Location destination) {
        Objects.requireNonNull(destination);
        var response = call("POST", "/quotes", Map.of("sender", Map.of("client_vendor_id", vendorId), "recipient", Map.of("location", location(destination))), false);
        var body = object(response, false);
        try {
            String id = identifier(body.path("quote_id").asString());
            if (!Set.of(200, 201).contains(response.status()) || !"NGN".equals(body.path("currency_code").asString()) || !vendorId.equals(body.path("sender").path("client_vendor_id").asString()) || !body.path("estimated_delivery_fee").isNumber()) throw invalid();
            var route = body.path("recipient").path("location");
            if (!route.path("latitude").isNumber() || !route.path("longitude").isNumber() || new BigDecimal(route.path("latitude").asString()).compareTo(destination.latitude()) != 0 || new BigDecimal(route.path("longitude").asString()).compareTo(destination.longitude()) != 0) throw invalid();
            var fee = money(new BigDecimal(body.path("estimated_delivery_fee").asString()));
            Instant expiry = Instant.parse(body.path("expired_at").asString());
            if (!expiry.isAfter(clock.instant()) || expiry.isAfter(clock.instant().plusSeconds(605))) throw invalid();
            return new Quote(id, fee, "NGN", expiry, accountHash);
        } catch (RuntimeException failure) { throw new ProviderHttp.Failure("PANDAGO_QUOTE_INVALID", false, response.status()); }
    }
    /** Read-only pre-checkout estimates. The time endpoint is historical, not live traffic data. */
    public Estimate estimate(UUID clientOrderId, Recipient recipient, BigDecimal orderSubtotal, String description) {
        if (clientOrderId == null || clientOrderId.equals(new UUID(0, 0)) || recipient == null ||
            orderSubtotal == null || orderSubtotal.signum() < 0 || !text(description, 1000).equals(description)) throw invalid();
        BigDecimal amount = money(orderSubtotal);
        var sender = Map.of("client_vendor_id", vendorId);
        var recipientBody = Map.of("name", recipient.name(), "phone_number", recipient.phone(), "location", location(recipient.location()));
        var request = Map.of("client_order_id", clientOrderId.toString(), "sender", sender, "recipient", recipientBody,
            "payment_method", "PAID", "amount", amount, "description", description);

        var feeResponse = call("POST", "/orders/fee", request, false);
        var feeBody = object(feeResponse, false);
        BigDecimal fee;
        try {
            if (feeResponse.status() != 200 || !clientOrderId.toString().equals(feeBody.path("client_order_id").asString()) ||
                !"NGN".equals(feeBody.path("currency_code").asString()) || !feeBody.path("estimated_delivery_fee").isNumber()) throw invalid();
            fee = money(new BigDecimal(feeBody.path("estimated_delivery_fee").asString()));
        } catch (RuntimeException failure) {
            throw new ProviderHttp.Failure("PANDAGO_FEE_ESTIMATE_INVALID", false, feeResponse.status());
        }

        var timeResponse = call("POST", "/orders/time", request, false);
        var timeBody = object(timeResponse, false);
        try {
            if (timeResponse.status() != 200 || !clientOrderId.toString().equals(timeBody.path("client_order_id").asString())) throw invalid();
            Instant pickup = Instant.parse(timeBody.path("estimated_pickup_time").asString());
            Instant delivery = Instant.parse(timeBody.path("estimated_delivery_time").asString());
            String duration = duration(timeBody.path("delivery_duration").asString());
            String lower = duration(timeBody.path("lower_bound").asString());
            String upper = duration(timeBody.path("upper_bound").asString());
            if (!pickup.isAfter(clock.instant()) || !delivery.isAfter(pickup) ||
                Duration.parse(lower).compareTo(Duration.parse(upper)) > 0) throw invalid();
            return new Estimate(fee, "NGN", pickup, delivery, duration, lower, upper);
        } catch (RuntimeException failure) {
            throw new ProviderHttp.Failure("PANDAGO_TIME_ESTIMATE_INVALID", false, timeResponse.status());
        }
    }
    private static String duration(String value) {
        text(value, 40);
        Duration parsed = Duration.parse(value);
        if (parsed.isNegative() || parsed.isZero()) throw invalid();
        return parsed.toString();
    }
    private static BigDecimal money(BigDecimal value) { if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("999999999999999.99")) > 0) throw invalid(); try { return value.setScale(2); } catch (ArithmeticException failure) { throw invalid(); } }
    /** Caller must persist this client reference and the confirmed quote before HTTP. No automatic retry. */
    public Order confirm(Quote quote, UUID clientOrderId, Recipient recipient, BigDecimal prepaidAmount, String description) {
        if (quote == null || !accountHash.equals(quote.accountHash()) || !"NGN".equals(quote.currency()) || !quote.expiresAt().isAfter(clock.instant()) || clientOrderId == null || clientOrderId.equals(new UUID(0, 0)) || recipient == null) throw invalid();
        var response = call("POST", "/quotes/" + identifier(quote.id()), Map.of("client_order_id", clientOrderId.toString(), "sender", Map.of("notes", clientOrderId.toString()), "recipient", Map.of("name", recipient.name(), "phone_number", recipient.phone()), "payment_method", "PAID", "amount", money(prepaidAmount), "currency_code", "NGN", "description", text(description, 500)), true);
        var result = order(object(response, true), true, response.status());
        if (!Set.of(200, 201).contains(response.status()) || !clientOrderId.toString().equals(result.clientOrderId())) throw new ProviderHttp.Failure("PANDAGO_BOOKING_UNCONFIRMED", true, response.status());
        return result;
    }
    public Order order(String orderId) {
        identifier(orderId); var response = call("GET", "/orders/" + orderId, null, false); var result = order(object(response, false), false, response.status());
        if (response.status() != 200 || !orderId.equals(result.id())) throw new ProviderHttp.Failure("PANDAGO_ORDER_MISMATCH", false, response.status());
        return result;
    }
    private static Order order(JsonNode body, boolean uncertain, int status) {
        try { String id = identifier(body.path("order_id").asString()), client = text(body.path("client_order_id").asString(), 160), state = body.path("status").asString(); if (!STATUSES.contains(state)) throw invalid(); return new Order(id, client, state); }
        catch (RuntimeException failure) { throw new ProviderHttp.Failure("PANDAGO_ORDER_INVALID", uncertain, status); }
    }
    /** A deprecated 203 acknowledgement requires an authoritative order read before local cancellation. */
    public Cancellation cancel(String orderId, String reason) {
        identifier(orderId); if (reason == null || !REASONS.contains(reason)) throw invalid();
        var response = call("DELETE", "/orders/" + orderId, Map.of("reason", reason), true);
        return switch (response.status()) { case 204 -> Cancellation.CANCELLED; case 203 -> Cancellation.LEGACY_ACKNOWLEDGED; default -> throw new ProviderHttp.Failure("PANDAGO_CANCELLATION_UNCONFIRMED", true, response.status()); };
    }
}
