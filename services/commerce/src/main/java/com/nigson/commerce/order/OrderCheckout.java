package com.nigson.commerce.order;
import java.util.UUID;
import com.nigson.shared.security.ActorAssertion;
/** Local caller transaction used by checkout and offered-quote acceptance. Never invokes providers. */
public interface OrderCheckout {
 OrderValues.Checkout pending(ActorAssertion.Actor actor,OrderValues.CheckoutInput input,UUID correlation);
}
