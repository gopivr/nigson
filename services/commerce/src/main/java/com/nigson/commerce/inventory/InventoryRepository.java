package com.nigson.commerce.inventory;
import java.util.UUID;
/** Owned stock persistence; no remote provider or shared entity. */
public interface InventoryRepository {
 record Changed(UUID balance,InventoryValues.Stock before,InventoryValues.Stock after){}
 InventoryValues.Page list(InventoryValues.Query query);
 InventoryValues.Location create(InventoryValues.NewLocation input,String actor);
 Changed adjust(InventoryValues.Adjustment input,long expected,String operation,String actor);
}
