package com.nigson.commerce.notification;
import java.util.UUID;
/** Reads/writes only commerce-owned minimum delivery context and outcome journal. */
public interface NotificationRepository {
    NotificationValues.Context context(UUID order);
    void requireOrder(UUID order,boolean lock);
    boolean record(NotificationValues.Delivery delivery,String principal);
}
