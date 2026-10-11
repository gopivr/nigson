package com.nigson.commerce.payment;
import java.util.UUID;
/** Claims and outcomes persist locally; all actual provider HTTP occurs between transactions. */
public interface RefundOperations {
 void submit(UUID refund,UUID correlation);
 void inspect(UUID refund,UUID correlation);
}
