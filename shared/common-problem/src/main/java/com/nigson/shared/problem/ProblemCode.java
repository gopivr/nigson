package com.nigson.shared.problem;

/** Closed safe wire vocabulary. Messages never come from an exception or request. */
public enum ProblemCode {
 VALIDATION_ERROR(400,"Invalid request","Supply valid request fields."),
 AUTH_REQUIRED(401,"Authentication required","Authenticate before continuing."),
 ACCESS_DENIED(403,"Access denied","This operation is not permitted."),
 NOT_FOUND(404,"Not found","The requested resource is unavailable."),
 CONFLICT(409,"Conflict","The operation conflicts with current state."),
 IDEMPOTENCY_HASH_MISMATCH(409,"Conflicting request","Use the original request with this key."),
 IDEMPOTENCY_IN_PROGRESS(409,"Request in progress","Retry this request after the current operation finishes."),
 VERSION_MISMATCH(412,"Version mismatch","Refresh the resource before retrying."),
 CHECKOUT_REPRICE_REQUIRED(409,"Checkout quote changed","Refresh the checkout quote before continuing."),
 ORDER_RULE_VIOLATION(422,"Order rule violation","The order operation does not meet current purchase rules."),
 FULFILLMENT_RULE_VIOLATION(422,"Fulfillment rule violation","The delivery or shipment operation does not meet current rules."),
 CART_RULE_VIOLATION(422,"Cart rule violation","The cart operation does not meet current shopping rules."),
 PRICING_RULE_VIOLATION(422,"Pricing rule violation","The pricing operation does not meet current commercial rules."),
 MARKET_RULE_VIOLATION(422,"Market rule violation","The market operation does not meet approved selling rules."),
 INVENTORY_RULE_VIOLATION(422,"Inventory rule violation","The stock operation does not meet current inventory rules."),
 NOTIFICATION_RULE_VIOLATION(422,"Notification rule violation","The notification operation does not meet current rules."),
 PROVIDER_RULE_VIOLATION(422,"Provider rule violation","The provider operation does not meet current rules."),
 PROVIDER_MODE_MISMATCH(422,"Provider mode mismatch","The configured provider target does not match this environment."),
 FILES_RULE_VIOLATION(422,"File rule violation","The file does not meet its purpose and safety requirements."),
 CATALOGUE_RULE_VIOLATION(422,"Catalogue rule violation","The catalogue change does not meet current rules."),
 PLATFORM_RULE_VIOLATION(422,"Configuration rule violation","The configuration does not meet registered owner rules."),
 PARTY_RULE_VIOLATION(422,"Party rule violation","The profile or address does not meet current rules."),
 AUTHORIZATION_RULE_VIOLATION(422,"Authorization rule violation","Retain an active administrator before removing this role."),
 BUSINESS_RULE_VIOLATION(422,"Ineligible operation","The operation does not meet its requirements."),
 INVALID_TRANSITION(409,"Invalid transition","The requested transition is unavailable."),
 FEATURE_DISABLED(422,"Feature unavailable","This feature is not active."),
 RATE_LIMITED(429,"Rate limit exceeded","Retry after the permitted interval."),
 PROVIDER_UNAVAILABLE(503,"Service unavailable","Retry a safe read later or inspect the existing operation."),
 PROVIDER_OUTCOME_UNKNOWN(503,"Outcome uncertain","Inspect the existing operation before another attempt."),
 INTERNAL_ERROR(500,"Internal error","The request could not be completed."),
 BODY_TOO_LARGE(413,"Request too large","Reduce the request size."),
 UNSUPPORTED_MEDIA_TYPE(415,"Unsupported content type","Use the specified content type."),
 METHOD_NOT_ALLOWED(405,"Method not allowed","Use a supported request method.");
 public final int status; public final String title,detail;
 ProblemCode(int status,String title,String detail){this.status=status;this.title=title;this.detail=detail;}
}
