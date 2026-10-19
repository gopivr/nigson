package com.nigson.commerce.cart;

import java.util.UUID;

public final class CartRecoveryValues {
    private CartRecoveryValues() {}
    public record Eligibility(boolean eligible, UUID partyId, String cartLink) {}
}
