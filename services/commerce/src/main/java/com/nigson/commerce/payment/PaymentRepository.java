package com.nigson.commerce.payment;
import java.util.UUID;
/** Commerce-owned local financial commands join the caller transaction; no provider HTTP. */
public interface PaymentRepository {
 PaymentValues.Payment payment(UUID order);
 PaymentValues.Refund refund(UUID id,boolean lock);
 PaymentValues.RefundDetail detail(UUID id);
 UUID order(UUID refund);
 PaymentValues.Refund request(UUID order,long expected,PaymentValues.RefundInput input,String businessKey,UUID actor);
 PaymentValues.Refund approve(UUID id,long expected,PaymentValues.Approval input,UUID actor,boolean twoPerson);
}
