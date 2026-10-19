package com.nigson.commerce.platform;

import com.nigson.shared.security.ActorAssertion;

/** Port for a Commerce owner's current ADMIN check used by read-only audit queries. */
public interface AuditAuthority {
    void requireCurrentAdministrator(ActorAssertion.Actor actor);
}
