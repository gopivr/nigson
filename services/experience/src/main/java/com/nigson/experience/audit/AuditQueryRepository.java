package com.nigson.experience.audit;

public interface AuditQueryRepository {
    AuditValues.Page search(AuditValues.Query query);
}
