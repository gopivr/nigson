package com.nigson.commerce.fulfillment;
import java.util.*;
import com.nigson.commerce.security.ActorDelegation;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
/** Source authority/intent -> unlocked delegated command -> durable source acknowledgement. */
public final class CarrierReconciliationService {
 private final CarrierReconciliationCommands commands;private final CarrierReconciliationPort remote;private final ActorDelegation delegation;
 public CarrierReconciliationService(CarrierReconciliationCommands commands,CarrierReconciliationPort remote,ActorDelegation delegation){this.commands=commands;this.remote=remote;this.delegation=delegation;}
 public CarrierReconciliationValues.Result reconcile(ActorAssertion.Actor actor,String session,UUID shipment,long expected,String reason,String key,UUID correlation){commands.authority(actor);if(session==null)throw ActorAssertion.denied();var intent=commands.claim(actor,shipment,expected,reason,key,correlation);if(intent.rejected()!=null)throw new Problem.Failure(intent.rejected());if(intent.completed()!=null)return intent.completed();CarrierReconciliationValues.Result result;
  try{var binding=new ActorAssertion.Binding("integration",HttpCarrierReconciliation.ROUTE.scope(),"POST",HttpCarrierReconciliation.ROUTE.path().replace("{id}",shipment.toString()),correlation,ActorAssertion.Binding.bodyHash(HttpCarrierReconciliation.body(intent.request())));String assertion=delegation.issue(session,binding,null);result=remote.reconcile(shipment,intent.request(),intent.command(),assertion,correlation);if(result==null||!shipment.equals(result.shipmentId()))throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);commands.finish(intent,actor,shipment,correlation,result,null);
  }catch(org.springframework.security.access.AccessDeniedException denied){commands.finish(intent,actor,shipment,correlation,null,ProblemCode.ACCESS_DENIED);throw denied;}catch(Problem.Failure failure){if(Set.of(ProblemCode.VALIDATION_ERROR,ProblemCode.ACCESS_DENIED,ProblemCode.INVALID_TRANSITION,ProblemCode.IDEMPOTENCY_HASH_MISMATCH,ProblemCode.PROVIDER_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH,ProblemCode.FEATURE_DISABLED).contains(failure.code)){commands.finish(intent,actor,shipment,correlation,null,failure.code);throw failure;}commands.unknown(intent,actor,shipment,correlation);throw failure;}catch(RuntimeException uncertain){commands.unknown(intent,actor,shipment,correlation);throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}commands.authority(actor);return result;
 }
}
