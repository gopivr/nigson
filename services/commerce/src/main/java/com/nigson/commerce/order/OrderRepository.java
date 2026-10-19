package com.nigson.commerce.order;
import java.util.*;
import com.nigson.shared.security.ActorAssertion;
/** All mutations join the caller's commerce transaction. */
public interface OrderRepository {
 void quoteOwner(ActorAssertion.Actor actor,UUID quote);
 record Created(OrderValues.Checkout checkout,boolean created){}
 Created checkout(ActorAssertion.Actor actor,OrderValues.CheckoutInput input,UUID correlation);
 OrderValues.Order find(UUID id,boolean lock);
 OrderValues.Payment payment(UUID order);
 OrderValues.Page list(ActorAssertion.Actor actor,OrderValues.Query query,boolean staff);
 OrderValues.Order processing(UUID id,long version,String actor,String reason);
 OrderValues.Order cancelled(UUID id,long version,String actor,String reason,UUID correlation);
 OrderValues.Proof proof(OrderValues.ProofInput input);
 OrderValues.AiStatus aiStatus(UUID order);
 UUID organization(UUID order);
}
