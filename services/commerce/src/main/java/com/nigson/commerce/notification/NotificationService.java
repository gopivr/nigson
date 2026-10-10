package com.nigson.commerce.notification;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.nigson.commerce.platform.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;
import com.nigson.shared.security.PrivateWorkload;
import io.micrometer.core.instrument.MeterRegistry;

public final class NotificationService {
    private final NotificationRepository repository;private final PlatformStore commands;private final AuditWriter audit;private final TransactionTemplate reads;private final MeterRegistry metrics;
    public NotificationService(NotificationRepository repository,PlatformStore commands,AuditWriter audit,PlatformTransactionManager manager,MeterRegistry metrics){this.repository=repository;this.commands=commands;this.audit=audit;this.metrics=metrics;reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setTimeout(2);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
    private void proof(PrivateWorkload.Proof proof,String api,String scope){if(proof==null||!proof.client().equals("nigson-integration")||proof.subject()==null||proof.subject().isBlank()||proof.actor()!=null||!proof.route().api().equals(api)||!proof.route().owner().equals("commerce")||!proof.route().client().equals("nigson-integration")||!proof.route().scope().equals(scope))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);}
    public NotificationValues.Context context(PrivateWorkload.Proof proof,UUID order){proof(proof,"API-122","notification.read");return reads.execute(status->repository.context(order));}
    public NotificationValues.Acknowledged delivery(PrivateWorkload.Proof proof,NotificationValues.Delivery delivery,String key,UUID correlation){
        proof(proof,"API-133","notification.write");ApiValues.idempotencyKey(key);repository.requireOrder(delivery.resourceId(),false);String principal="service:nigson-integration";
        var response=commands.executeLocal(principal,"API-133",key,EventCodec.JSON.valueToTree(delivery),jdbc->{
            jdbc.execute("SET LOCAL lock_timeout='350ms'");jdbc.execute("SET LOCAL statement_timeout='800ms'");
            if(repository.record(delivery,principal)){org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){metrics.counter("nigson.notification.outcomes","state",delivery.state()).increment();}});audit.append(principal,AuditValues.ActorKind.SERVICE,AuditValues.Action.NOTIFICATION_DELIVERY,AuditValues.Resource.NOTIFICATION,delivery.resourceId(),correlation,AuditValues.Reason.VERIFIED_PROVIDER,null,Map.of("state",delivery.state()),null);}
            return new PlatformStore.Response(200,EventCodec.JSON.valueToTree(new NotificationValues.Acknowledged(true)),delivery.resourceId());
        });
        return EventCodec.JSON.treeToValue(response.body(),NotificationValues.Acknowledged.class);
    }
}
