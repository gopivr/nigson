package com.nigson.integration.carrier;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.nigson.shared.problem.*;
@RestController
@ConditionalOnProperty(name="NIGSON_CARRIER_CANCELLATION_ENABLED",havingValue="false",matchIfMissing=true)
public final class CarrierCancellationUnavailableController {
 @PostMapping("/internal/v1/carrier/cancellations")public void unavailable(){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
}
