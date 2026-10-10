package com.nigson.commerce.notification;
import java.util.*;
import com.nigson.commerce.security.ActorDelegation;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;
import io.micrometer.core.instrument.MeterRegistry;

public final class TemplateService {
    private final TemplateCommands commands;private final TemplatePort upstream;private final ActorDelegation delegation;private final MeterRegistry metrics;
    public TemplateService(TemplateCommands commands,TemplatePort upstream,ActorDelegation delegation,MeterRegistry metrics){this.commands=commands;this.upstream=upstream;this.delegation=delegation;this.metrics=metrics;}
    public TemplatePort.Accepted put(ActorAssertion.Actor actor,String session,TemplateValues.Template template,Long version,String key,UUID correlation){
        commands.authority(actor,true);if(!upstream.configured())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);if(session==null)throw ActorAssertion.denied();
        var intent=commands.claim(actor,template,version,key,correlation);if(intent.rejected()!=null)throw new Problem.Failure(intent.rejected());if(intent.completed()!=null)return intent.completed();UUID resource=TemplateCommands.resource(template);TemplatePort.Accepted result;
        try{var binding=new ActorAssertion.Binding("integration","templates.write_WITH_SIGNED_ADMIN","POST","/internal/v1/templates",correlation,ActorAssertion.Binding.bodyHash(EventCodec.JSON.writeValueAsBytes(TemplateValues.wire(template))));String assertion=delegation.issue(session,binding,null);
            result=upstream.put(template,version,intent.command().toString(),assertion,correlation);commands.finish(intent,actor,resource,correlation,result,null);metrics.counter("nigson.notification.templates","outcome","ACKNOWLEDGED").increment();
        }catch(org.springframework.security.access.AccessDeniedException denied){commands.finish(intent,actor,resource,correlation,null,ProblemCode.ACCESS_DENIED);throw denied;
        }catch(Problem.Failure failure){if(Set.of(ProblemCode.VALIDATION_ERROR,ProblemCode.ACCESS_DENIED,ProblemCode.NOT_FOUND,ProblemCode.CONFLICT,ProblemCode.VERSION_MISMATCH,ProblemCode.INVALID_TRANSITION,ProblemCode.NOTIFICATION_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH,ProblemCode.FEATURE_DISABLED).contains(failure.code)){commands.finish(intent,actor,resource,correlation,null,failure.code);throw failure;}unknown(intent,actor,resource,correlation);throw failure;
        }catch(RuntimeException uncertain){unknown(intent,actor,resource,correlation);throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
        commands.authority(actor,true);return result;
    }
    private void unknown(TemplateCommands.Intent intent,ActorAssertion.Actor actor,UUID resource,UUID correlation){commands.unknown(intent,actor,resource,correlation);metrics.counter("nigson.notification.templates","outcome","UNKNOWN").increment();}
}
