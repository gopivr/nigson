package com.nigson.commerce.cart;
import java.util.UUID;
/** Commerce-local caller transaction. T1 locks cart first; settlement/expiry lock order first.
 * These guards change only cart state, never order/payment/stock truth or retention. */
public interface CartCheckout {
 CartValues.Cart pending(UUID party,UUID cart,long expected,UUID quote,String quoteHash,UUID order,UUID correlation);
 CartValues.Cart converted(UUID order,UUID correlation);
 CartValues.Cart expired(UUID order,UUID correlation);
 boolean recoveryEligible(UUID party,UUID cart);
}
