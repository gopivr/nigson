package com.nigson.commerce.order;
import java.util.UUID;
/** Payment-owned orchestration after T1 commits. No returned provider data may authorize stock directly. */
public interface CheckoutPayments {
 void requireReady();
 void initialize(UUID order,UUID correlation);
 void inspect(UUID order,UUID correlation);
}
