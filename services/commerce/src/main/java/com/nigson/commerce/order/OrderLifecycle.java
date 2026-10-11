package com.nigson.commerce.order;
import java.util.UUID;
/** Caller transaction after independent payment verification. Financial records remain authoritative. */
public interface OrderLifecycle {
 OrderValues.Order settled(UUID order,UUID correlation);
}
