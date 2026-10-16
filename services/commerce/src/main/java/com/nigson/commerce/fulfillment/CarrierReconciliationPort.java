package com.nigson.commerce.fulfillment;
import java.util.UUID;
public interface CarrierReconciliationPort {
 CarrierReconciliationValues.Result reconcile(UUID shipment,CarrierReconciliationValues.Request input,UUID command,String assertion,UUID correlation);
}
