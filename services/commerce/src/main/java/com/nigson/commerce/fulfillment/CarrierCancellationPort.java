package com.nigson.commerce.fulfillment;
import java.util.UUID;
/** Fixed authenticated source-intent relay; no browser credentials or actor token. */
public interface CarrierCancellationPort {
 CarrierCancellationValues.Acknowledgement exchange(CarrierCancellationValues.Command command,UUID correlation);
}
