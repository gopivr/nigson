package com.nigson.integration.provider;

import java.time.*;
import java.util.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.integration.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.*;

/** Private commands act on integration-owned records; remote side effects occur elsewhere. */
public final class JobService {
 private final JobRepository repository;private final PlatformStore store;private final AuditWriter audit;private final TransactionTemplate read;private final Clock clock;
 public JobService(JobRepository repository,PlatformStore store,AuditWriter audit,PlatformTransactionManager manager,Clock clock){
  this.repository=repository;this.store=store;this.audit=audit;this.clock=clock;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
 }
 private void proof(PrivateWorkload.Proof proof,String api){
  var route=PrivateWorkload.ROUTES.stream().filter(r->r.api().equals(api)).findFirst().orElseThrow(ActorAssertion::denied);
  if(proof==null||!route.equals(proof.route())||!proof.client().equals(route.client())||proof.subject()==null||!proof.subject().matches("[\\x21-\\x7e]{1,100}"))throw ActorAssertion.denied();
  if(api.equals("API-140")){if(proof.actor()!=null)throw ActorAssertion.denied();}
  else{var actor=proof.actor();if(actor==null||actor.organization()!=null||!actor.roles().contains("OPERATIONS")||!actor.scopes().contains(route.scope()))throw ActorAssertion.denied();ActorAssertion.recentMfa(actor,clock);}
 }
 public JobValues.Page list(PrivateWorkload.Proof proof,JobValues.Query query){proof(proof,"API-140");Objects.requireNonNull(query);return read.execute(status->repository.list(query));}
 public JobValues.Accepted retry(PrivateWorkload.Proof proof,UUID id,long expected,JobValues.Retry body,String key,UUID correlation){
  proof(proof,"API-141");if(id==null||id.equals(new UUID(0,0))||expected<0||body==null||correlation==null||correlation.equals(new UUID(0,0)))throw ApiValues.invalid();ApiValues.idempotencyKey(key);
  String principal="nigson-commerce:OPERATIONS:"+proof.actor().party();var input=EventCodec.JSON.valueToTree(Map.of("id",id.toString(),"version",expected,"reason",body.reason()));
  var result=store.executeLocal(principal,"API-141",key,input,db->{
   proof(proof,"API-141");var before=repository.find(id,true);var accepted=repository.retry(id,expected,proof.actor().party().toString());
   audit.append(proof.actor().party().toString(),AuditValues.ActorKind.USER,AuditValues.Action.QUEUE_REPLAY,AuditValues.Resource.QUEUE,id,correlation,AuditValues.Reason.OPERATOR_REQUEST,Map.of("state",before.state().name(),"version",before.version()),Map.of("state",accepted.state().name(),"version",accepted.version()),null);
   return new PlatformStore.Response(202,EventCodec.JSON.valueToTree(Map.of("job",accepted.view(),"version",accepted.version())),id);
  });
  var saved=result.body();return new JobValues.Accepted(EventCodec.JSON.treeToValue(saved.path("job"),JobValues.Job.class),saved.path("version").asLong());
 }
}
