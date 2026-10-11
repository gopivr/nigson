package com.nigson.commerce.payment;
import java.util.Set;
/** Explicit financial activation and conservative two-person approval policy. */
public record PaymentPolicy(boolean enabled,String environment,boolean twoPerson,String approvalPolicyReference,int uncertainGraceSeconds){
 public PaymentPolicy{if(!Set.of("local","test","dev","qa","stage","prod").contains(environment)||approvalPolicyReference==null||approvalPolicyReference.length()>120||uncertainGraceSeconds<60||uncertainGraceSeconds>3600)throw new IllegalArgumentException("PAYMENT_POLICY_INVALID");if(!twoPerson&&!Set.of("local","test").contains(environment)&&!approvalPolicyReference.matches("[A-Za-z0-9._/-]{1,120}"))throw new IllegalArgumentException("PAYMENT_TWO_PERSON_APPROVAL_REQUIRED");}
 public void require(){if(!enabled)throw new com.nigson.shared.problem.Problem.Failure(com.nigson.shared.problem.ProblemCode.FEATURE_DISABLED);}
}
