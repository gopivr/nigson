package com.nigson.integration.providers;

import java.time.LocalDate;
import tools.jackson.databind.JsonNode;

/** Integration-private HTTP protocol port. Owned workflows persist intent and interpret outcomes. */
public interface CarrierProvider {
 com.nigson.shared.security.ProviderHttp.Mode mode();
 /** Nonsecret stable identity; rotating credentials for the same account does not change it. */
 String accountFingerprint();
 JsonNode rates(JsonNode shipment);
 JsonNode book(JsonNode shipment);
 /** Read-only lookup of an accepted asynchronous booking; never creates another shipment. */
 JsonNode confirmed(String jobId);
 JsonNode cancel(String tracking);
 JsonNode track(String tracking);
 JsonNode trackReference(String reference,LocalDate begin,LocalDate end);
}
