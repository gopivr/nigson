package com.nigson.integration.providers;

import com.nigson.shared.events.EventCodec;
import com.nigson.shared.security.ProviderHttp;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class PandagoAdapterTest {
    static KeyPair keys;
    HttpServer server;
    TestClock clock;
    PandagoAdapter adapter;
    final AtomicInteger tokens = new AtomicInteger(), bookings = new AtomicInteger();
    final Map<String, Map<String, Object>> orders = new HashMap<>();
    final Set<String> assertions = new HashSet<>();
    int authStatus = 200, cancellationStatus = 204, quoteStatus = 201, bookingStatus = 201;
    boolean loseBookingResponse, malformedBooking, wrongCurrency, wrongReference;
    String pem;
    final PandagoAdapter.Location destination = new PandagoAdapter.Location("Synthetic delivery address", new BigDecimal("6.45"), new BigDecimal("3.39"));

    @BeforeAll static void keys() throws Exception { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); keys = generator.generateKeyPair(); }
    @BeforeEach void start() throws Exception {
        clock = new TestClock();
        pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try { handle(exchange); }
            catch (Throwable failure) { reply(exchange, 500, Map.of("error", "mock_protocol_rejected")); }
        });
        server.start(); adapter = adapter("mock-outlet");
    }
    @AfterEach void stop() { TransactionSynchronizationManager.setActualTransactionActive(false); server.stop(0); }
    PandagoAdapter adapter(String vendor) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new PandagoAdapter("test", ProviderHttp.Mode.MOCK, base, base, "mock-client", "mock-key", "pandago.api.ng.*", vendor, pem, clock);
    }
    void handle(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
        byte[] input = exchange.getRequestBody().readAllBytes();
        if (path.equals("/oauth2/token")) {
            var form = new HashMap<String, String>();
            for (String pair : new String(input, StandardCharsets.UTF_8).split("&")) { var parts = pair.split("=", 2); form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8)); }
            assertThat(method).isEqualTo("POST"); assertThat(form.get("grant_type")).isEqualTo("client_credentials"); assertThat(form.get("scope")).isEqualTo("pandago.api.ng.*");
            assertThat(form.get("client_assertion_type")).isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            String[] jwt = form.get("client_assertion").split("\\.");
            var header = EventCodec.JSON.readTree(Base64.getUrlDecoder().decode(jwt[0])); var claims = EventCodec.JSON.readTree(Base64.getUrlDecoder().decode(jwt[1]));
            assertThat(header.path("kid").asString()).isEqualTo("mock-key"); assertThat(header.path("alg").asString()).isEqualTo("RS256");
            assertThat(claims.path("iss").asString()).isEqualTo("mock-client"); assertThat(claims.path("sub").asString()).isEqualTo("mock-client"); assertThat(claims.path("aud").asString()).isEqualTo("https://sts.deliveryhero.io");
            assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(60);
            assertThat(assertions.add(claims.path("jti").asString())).isTrue();
            var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(keys.getPublic()); verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII)); assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
            int generation = tokens.incrementAndGet(); reply(exchange, authStatus, authStatus == 200 ? Map.of("access_token", "mock-token-" + generation, "expires_in", 120) : Map.of("error", "invalid_client")); return;
        }
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Bearer mock-token-");
        if (path.equals("/ng/api/v1/outlets/mock-outlet")) { reply(exchange, 200, Map.of("client_vendor_id", "mock-outlet", "currency", "NGN")); return; }
        if (path.equals("/ng/api/v1/orders/fee")) {
            assertThat(method).isEqualTo("POST"); var body = EventCodec.JSON.readTree(input); String reference = body.path("client_order_id").asString();
            assertThat(body.path("amount").decimalValue()).isEqualByComparingTo("50000.00");
            reply(exchange, 200, Map.of("client_order_id", reference, "estimated_delivery_fee", new BigDecimal("1300.00"), "currency_code", "NGN")); return;
        }
        if (path.equals("/ng/api/v1/orders/time")) {
            assertThat(method).isEqualTo("POST"); var body = EventCodec.JSON.readTree(input); String reference = body.path("client_order_id").asString();
            reply(exchange, 200, Map.of("client_order_id", reference, "estimated_pickup_time", clock.instant().plusSeconds(600).toString(),
                "estimated_delivery_time", clock.instant().plusSeconds(4200).toString(), "delivery_duration", "PT1H",
                "lower_bound", "PT45M", "upper_bound", "PT1H30M")); return;
        }
        if (path.equals("/ng/api/v1/quotes")) {
            assertThat(method).isEqualTo("POST"); var body = EventCodec.JSON.readTree(input);
            assertThat(body.path("sender").path("client_vendor_id").asString()).isEqualTo("mock-outlet");
            assertThat(body.path("recipient").path("location").path("latitude").decimalValue()).isEqualByComparingTo(destination.latitude());
            reply(exchange, quoteStatus, Map.of("quote_id", "quote-1", "estimated_delivery_fee", new BigDecimal("1250.50"), "currency_code", wrongCurrency ? "USD" : "NGN", "expired_at", clock.instant().plusSeconds(600).toString(), "sender", Map.of("client_vendor_id", "mock-outlet"), "recipient", Map.of("location", Map.of("latitude", destination.latitude(), "longitude", destination.longitude())))); return;
        }
        if (path.equals("/ng/api/v1/quotes/quote-1")) {
            assertThat(method).isEqualTo("POST"); var body = EventCodec.JSON.readTree(input); String reference = body.path("client_order_id").asString();
            assertThat(body.path("payment_method").asString()).isEqualTo("PAID"); assertThat(body.path("currency_code").asString()).isEqualTo("NGN");
            assertThat(body.path("sender").path("notes").asString()).isEqualTo(reference);
            if (orders.containsKey(reference)) { reply(exchange, 409, Map.of("error", "duplicate_reference")); return; }
            bookings.incrementAndGet(); var order = Map.<String, Object>of("order_id", "order-1", "client_order_id", wrongReference ? UUID.randomUUID().toString() : reference, "status", "NEW"); orders.put(reference, order);
            reply(exchange, loseBookingResponse ? 503 : bookingStatus, malformedBooking ? Map.of("accepted", true) : order); return;
        }
        if (path.equals("/ng/api/v1/orders/order-1")) {
            if (method.equals("DELETE")) { var body = EventCodec.JSON.readTree(input); assertThat(body.path("reason").asString()).isEqualTo("MISTAKE_ERROR"); reply(exchange, cancellationStatus, Map.of()); }
            else reply(exchange, 200, orders.values().iterator().next()); return;
        }
        reply(exchange, 404, Map.of("error", "not_found"));
    }
    static void reply(HttpExchange exchange, int status, Object body) throws java.io.IOException {
        byte[] bytes = EventCodec.JSON.writeValueAsBytes(body); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length); if (status != 204) exchange.getResponseBody().write(bytes); exchange.close();
    }
    PandagoAdapter.Order book(UUID reference) { return adapter.confirm(adapter.quote(destination), reference, new PandagoAdapter.Recipient("Synthetic recipient", "+2348012345678", destination), new BigDecimal("20000.00"), "Synthetic prepaid electronics"); }
    @Test void precheckoutFeeAndHistoricalTimeEstimatesUseCommerceSubtotal() {
        UUID clientOrderId = UUID.randomUUID();
        var estimate = adapter.estimate(clientOrderId, new PandagoAdapter.Recipient("Synthetic recipient", "+2348012345678", destination),
            new BigDecimal("50000.00"), "Nigson checkout order");
        assertThat(estimate.fee()).isEqualByComparingTo("1300.00");
        assertThat(estimate.currency()).isEqualTo("NGN");
        assertThat(estimate.estimatedPickupTime()).isEqualTo(clock.instant().plusSeconds(600));
        assertThat(estimate.estimatedDeliveryTime()).isEqualTo(clock.instant().plusSeconds(4200));
        assertThat(estimate.deliveryDuration()).isEqualTo("PT1H");
        assertThat(estimate.lowerBound()).isEqualTo("PT45M");
        assertThat(estimate.upperBound()).isEqualTo("PT1H30M");
    }
    @Test void signedOAuthOutletQuoteBookingReadAndCancellationUseRealHttp() {
        assertThat(adapter.outlet().path("currency").asString()).isEqualTo("NGN");
        var quote = adapter.quote(destination); assertThat(quote.fee()).isEqualByComparingTo("1250.50");
        var order = book(UUID.randomUUID()); assertThat(order.status()).isEqualTo("NEW"); assertThat(adapter.order(order.id())).isEqualTo(order);
        assertThat(adapter.cancel(order.id(), "MISTAKE_ERROR")).isEqualTo(PandagoAdapter.Cancellation.CANCELLED);
        assertThat(tokens.get()).isEqualTo(1); assertThat(bookings.get()).isEqualTo(1);
    }
    @Test void expiryRefreshesSignedAssertionAndNeverLeaksTheToken() { adapter.outlet(); clock.now = clock.now.plusSeconds(100); adapter.outlet(); assertThat(tokens.get()).isEqualTo(2); assertThat(assertions).hasSize(2); }
    @Test void stageSheet200QuoteAndConfirmationResponsesAreSupported() {
        quoteStatus = 200; bookingStatus = 200; UUID reference = UUID.randomUUID();
        assertThat(book(reference).clientOrderId()).isEqualTo(reference.toString()); assertThat(bookings.get()).isEqualTo(1);
    }
    @Test void stageSheet200ResponseDoesNotHideAnUnconfirmedBooking() {
        quoteStatus = 200; bookingStatus = 200; malformedBooking = true;
        assertThatThrownBy(() -> book(UUID.randomUUID())).isInstanceOfSatisfying(ProviderHttp.Failure.class, f -> assertThat(f.uncertain).isTrue());
    }
    @Test void uncertainBookingAndDuplicateReferenceNeverCreateTwoOrders() {
        loseBookingResponse = true; UUID reference = UUID.randomUUID();
        assertThatThrownBy(() -> book(reference)).isInstanceOfSatisfying(ProviderHttp.Failure.class, f -> assertThat(f.uncertain).isTrue());
        assertThat(bookings.get()).isEqualTo(1);
        assertThatThrownBy(() -> book(reference)).isInstanceOfSatisfying(ProviderHttp.Failure.class, f -> { assertThat(f.uncertain).isTrue(); assertThat(f.status).isEqualTo(409); });
        assertThat(bookings.get()).isEqualTo(1); assertThat(adapter.order("order-1").clientOrderId()).isEqualTo(reference.toString());
    }
    @Test void malformedAcceptedBookingRemainsUnknown() { malformedBooking = true; assertThatThrownBy(() -> book(UUID.randomUUID())).isInstanceOfSatisfying(ProviderHttp.Failure.class, f -> assertThat(f.uncertain).isTrue()); assertThat(bookings.get()).isEqualTo(1); }
    @Test void wrongBookingReferenceRemainsUnknown() { wrongReference = true; assertThatThrownBy(() -> book(UUID.randomUUID())).isInstanceOfSatisfying(ProviderHttp.Failure.class, f -> assertThat(f.uncertain).isTrue()); }
    @Test void rejectedOAuthNeverSubmitsABooking() { authStatus = 401; assertThatThrownBy(() -> book(UUID.randomUUID())).isInstanceOfSatisfying(ProviderHttp.Failure.class, f -> assertThat(f.uncertain).isFalse()); assertThat(bookings.get()).isZero(); }
    @Test void wrongCurrencyAndExpiredQuotesFailBeforeBooking() {
        wrongCurrency = true; assertThatThrownBy(() -> adapter.quote(destination)).isInstanceOf(ProviderHttp.Failure.class); wrongCurrency = false;
        var quote = adapter.quote(destination); clock.now = clock.now.plusSeconds(601);
        assertThatThrownBy(() -> adapter.confirm(quote, UUID.randomUUID(), new PandagoAdapter.Recipient("Synthetic", "+2348012345678", destination), BigDecimal.ONE, "Synthetic order")).isInstanceOf(IllegalArgumentException.class); assertThat(bookings.get()).isZero();
    }
    @Test void deprecatedCancellationIsNotReportedAsConfirmed() { book(UUID.randomUUID()); cancellationStatus = 203; assertThat(adapter.cancel("order-1", "MISTAKE_ERROR")).isEqualTo(PandagoAdapter.Cancellation.LEGACY_ACKNOWLEDGED); }
    @Test void wrongAccountAndUnregisteredCancellationReasonAreRejected() {
        var quote = adapter.quote(destination); assertThatThrownBy(() -> adapter("mock-other-outlet").confirm(quote, UUID.randomUUID(), new PandagoAdapter.Recipient("Synthetic", "+2348012345678", destination), BigDecimal.ONE, "Synthetic order")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.cancel("order-1", "arbitrary reason")).isInstanceOf(IllegalArgumentException.class); assertThat(bookings.get()).isZero();
    }
    @Test void productionCannotUseMockOrWrongEnvironmentOrAnInvalidKey() {
        assertThatThrownBy(() -> new PandagoAdapter("prod", ProviderHttp.Mode.MOCK, "http://pandago-mock:8080", "http://pandago-mock:8080", "mock-client", "mock-key", "pandago.api.ng.*", "mock-outlet", pem, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PandagoAdapter("local", ProviderHttp.Mode.TEST, "https://ondemand-api-glovoapp.deliveryhero.io", "https://sts-st.deliveryhero.io", "client", "key", "pandago.api.ng.*", "outlet", pem, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PandagoAdapter("test", ProviderHttp.Mode.TEST, "https://api-infra-eu-central-1.stg.ondemandrider.net", "https://sts-st.deliveryhero.io", "client", "key", "pandago.api.ng.*", "outlet", "invalid", clock)).hasMessage("PANDAGO_PRIVATE_KEY_INVALID");
    }
    @Test void transactionAndMissingOrInvalidGeolocationFailClosed() {
        assertThatThrownBy(() -> new PandagoAdapter.Location("Synthetic", null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PandagoAdapter.Location("Synthetic", new BigDecimal("91"), BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true); assertThatThrownBy(() -> adapter.outlet()).isInstanceOf(IllegalStateException.class); assertThat(tokens.get()).isZero();
    }
    @Test void legacy1024BitKeyIsRejectedBeforeAnyHttp() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(1024);
        String weak = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        assertThatThrownBy(() -> new PandagoAdapter("test", ProviderHttp.Mode.TEST, "https://api-infra-eu-central-1.stg.ondemandrider.net", "https://sts-st.deliveryhero.io", "client", "key", "pandago.api.ng.*", "outlet", weak, clock)).hasMessage("PANDAGO_PRIVATE_KEY_INVALID");
        assertThat(tokens.get()).isZero();
    }
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId zone) { return this; } public Instant instant() { return now; }
    }
}
