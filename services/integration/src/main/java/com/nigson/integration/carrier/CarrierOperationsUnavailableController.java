package com.nigson.integration.carrier;
import java.util.List;
import com.nigson.shared.problem.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
/** Preserve fail-closed private API behavior after retiring unsupported execution. */
@RestController
@ConditionalOnProperty(name="NIGSON_SECURITY_ENABLED",havingValue="true")
public final class CarrierOperationsUnavailableController {
 @PostMapping({"/internal/v1/carrier/cancellations","/internal/v1/carrier/shipments/{id}/reconciliation"})
 public void unavailable(){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE,List.of(new Problem.Violation("provider","CONFIGURATION_REQUIRED","Automatic carrier execution is not configured. No provider operation was submitted.")));}
}
