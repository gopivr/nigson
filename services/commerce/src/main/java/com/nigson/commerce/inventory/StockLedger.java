package com.nigson.commerce.inventory;
import java.util.*;
import java.time.Instant;
/** Local application port only: caller authorizes the order operation and owns the surrounding
 * transaction (checkout, verified settlement, cancellation or fulfillment). No HTTP controller.
 * Actor is the verified party UUID for user-originated commands, or a server-owned scheduler
 * subject for system commands; never supplied by a browser body.
 * The adapter also locks the owned order, checks authoritative states and sorts all balance locks. */
public interface StockLedger {
 record Line(UUID orderLineId,UUID balanceId,String quantity){}
 enum ReturnKind { PHYSICAL_RETURN,UNSHIPPED_CANCEL }
 List<InventoryValues.Stock> reserve(UUID order,Map<UUID,String> quantities,Instant expiresAt,UUID command,String actor,UUID correlation);
 List<InventoryValues.Stock> allocate(UUID order,UUID command,String actor,UUID correlation);
 List<InventoryValues.Stock> release(UUID order,UUID command,String actor,UUID correlation);
 List<InventoryValues.Stock> dispatch(UUID order,List<Line> lines,UUID command,String actor,UUID correlation);
 List<InventoryValues.Stock> returned(UUID order,List<Line> lines,ReturnKind kind,String receipt,String reason,UUID command,String actor,UUID correlation);
}
