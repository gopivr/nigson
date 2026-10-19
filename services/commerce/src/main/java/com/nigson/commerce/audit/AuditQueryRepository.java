package com.nigson.commerce.audit;

public interface AuditQueryRepository {
    AuditValues.Page search(AuditValues.Query query);
}
