package com.nigson.commerce.reward;
import java.util.UUID;
/** Same-transaction Commerce reward hooks for verified order/refund facts. */
public interface RewardOperations {
 void orderSettled(UUID order,UUID correlation);
 void refundSettled(UUID refund,UUID correlation);
 default void qualifyDue(){}
 static RewardOperations none(){return new RewardOperations(){public void orderSettled(UUID order,UUID correlation){}public void refundSettled(UUID refund,UUID correlation){}};}
}
