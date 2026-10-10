package com.nigson.commerce.notification;
import java.util.*;
import java.math.RoundingMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.*;

public final class JdbcNotificationRepository implements NotificationRepository {
    private final JdbcTemplate jdbc;public JdbcNotificationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public NotificationValues.Context context(UUID order){
        var rows=jdbc.query("SELECT o.order_number,p.id,p.email,o.grand_total,o.currency_code,s.tracking_url FROM commerce.sales_order o JOIN commerce.party p ON p.id=o.party_id LEFT JOIN LATERAL (SELECT tracking_url FROM commerce.shipment WHERE order_id=o.id AND status IN ('SHIPPED','DELIVERED') ORDER BY dispatched_at DESC NULLS LAST,created_at DESC,id LIMIT 1) s ON true WHERE o.id=? AND p.status='ACTIVE' AND p.email_verified=true",(r,n)->new NotificationValues.Context(r.getString(1),new NotificationValues.Recipient(r.getObject(2,UUID.class),r.getString(3)),r.getBigDecimal(4).setScale(2,RoundingMode.UNNECESSARY).toPlainString(),r.getString(5).strip(),NotificationValues.tracking(r.getString(6))),order);
        if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);if(!rows.getFirst().currency().equals("NGN"))throw new Problem.Failure(ProblemCode.NOTIFICATION_RULE_VIOLATION);return rows.getFirst();
    }
    public void requireOrder(UUID order,boolean lock){if(lock&&!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");var rows=jdbc.query("SELECT id FROM commerce.sales_order WHERE id=?"+(lock?" FOR SHARE":""),(r,n)->r.getObject(1,UUID.class),order);if(rows.size()!=1)throw new Problem.Failure(ProblemCode.NOT_FOUND);}
    public boolean record(NotificationValues.Delivery delivery,String principal){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("LOCAL_TRANSACTION_REQUIRED");requireOrder(delivery.resourceId(),true);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"notification-job:"+delivery.jobId());
        var resources=jdbc.query("SELECT resource_id FROM commerce.idempotency_record WHERE principal_key=? AND operation IN ('API-133-OUTCOME-SENT','API-133-OUTCOME-FAILED','API-133-OUTCOME-SUPPRESSED','API-133-OUTCOME-UNKNOWN') AND request_key=? LIMIT 4",(r,n)->r.getObject(1,UUID.class),principal,delivery.jobId().toString());if(resources.stream().anyMatch(id->!id.equals(delivery.resourceId())))throw new Problem.Failure(ProblemCode.ACCESS_DENIED);
        String operation="API-133-OUTCOME-"+delivery.state(),key=delivery.jobId().toString(),hash=EventCodec.hash(EventCodec.JSON.valueToTree(delivery));UUID id=EventCodec.uuid7(java.time.Instant.now());
        int inserted=jdbc.update("INSERT INTO commerce.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,response_status,response_body,resource_id,expires_at) VALUES (?,?,?,?,?,?,?,'SUCCEEDED',200,'{\"acknowledged\":true}'::jsonb,?,now()+interval '1 day') ON CONFLICT(principal_key,operation,request_key) DO NOTHING",id,principal,principal,principal,operation,key,hash,delivery.resourceId());
        if(inserted==1)return true;String existing=jdbc.queryForObject("SELECT request_hash FROM commerce.idempotency_record WHERE principal_key=? AND operation=? AND request_key=?",String.class,principal,operation,key);if(!hash.equals(existing))throw new Problem.Failure(ProblemCode.IDEMPOTENCY_HASH_MISMATCH);return false;
    }
}
