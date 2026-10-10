package com.nigson.commerce.provider;

import java.util.*;
import com.nigson.commerce.security.*;
import com.nigson.shared.security.ActorAssertion;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import io.micrometer.core.instrument.MeterRegistry;

public final class JobService {
    private final JobCommands commands;private final IntegrationJobs upstream;private final ActorDelegation delegation;private final MeterRegistry metrics;
    public JobService(JobCommands commands,IntegrationJobs upstream,ActorDelegation delegation,MeterRegistry metrics){this.commands=commands;this.upstream=upstream;this.delegation=delegation;this.metrics=metrics;}
    public JobValues.Page list(ActorAssertion.Actor actor,JobValues.Query query,UUID correlation){commands.authority(actor,false);enabled();var result=upstream.list(query,correlation);commands.authority(actor,false);return result;}
    public IntegrationJobs.Accepted retry(ActorAssertion.Actor actor,String session,UUID job,long version,JobValues.Retry body,String key,UUID correlation){
        commands.authority(actor,true);enabled();if(session==null)throw ActorAssertion.denied();
        var intent=commands.claim(actor,job,version,body,key,correlation);
        if(intent.rejected()!=null)throw new Problem.Failure(intent.rejected());if(intent.completed()!=null)return intent.completed();
        IntegrationJobs.Accepted result;
        try{
            var binding=new ActorAssertion.Binding("integration","jobs.retry_WITH_SIGNED_STAFF","POST","/internal/v1/jobs/"+job+"/retry",correlation,ActorAssertion.Binding.bodyHash(EventCodec.JSON.writeValueAsBytes(body)));
            String assertion=delegation.issue(session,binding,null);
            var accepted=upstream.retry(job,version,body,intent.command().toString(),assertion,correlation);
            commands.finish(intent,actor,job,correlation,accepted,null);metrics.counter("nigson.provider.commands","outcome","ACKNOWLEDGED").increment();
            result=accepted;
        }catch(org.springframework.security.access.AccessDeniedException denied){
            commands.finish(intent,actor,job,correlation,null,ProblemCode.ACCESS_DENIED);throw denied;
        }catch(Problem.Failure failure){
            if(Set.of(ProblemCode.VALIDATION_ERROR,ProblemCode.ACCESS_DENIED,ProblemCode.NOT_FOUND,ProblemCode.VERSION_MISMATCH,ProblemCode.INVALID_TRANSITION,ProblemCode.PROVIDER_RULE_VIOLATION,ProblemCode.PROVIDER_MODE_MISMATCH,ProblemCode.FEATURE_DISABLED).contains(failure.code)){commands.finish(intent,actor,job,correlation,null,failure.code);throw failure;}
            unknown(intent,actor,job,correlation);throw failure;
        }catch(RuntimeException uncertain){unknown(intent,actor,job,correlation);throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
        commands.authority(actor,true);return result;
    }
    private void unknown(JobCommands.Intent intent,ActorAssertion.Actor actor,UUID job,UUID correlation){commands.unknown(intent,actor,job,correlation);metrics.counter("nigson.provider.commands","outcome","UNKNOWN").increment();}
    private void enabled(){if(!upstream.configured())throw new Problem.Failure(ProblemCode.FEATURE_DISABLED);}
}
