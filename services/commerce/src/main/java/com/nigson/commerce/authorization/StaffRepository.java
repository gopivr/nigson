package com.nigson.commerce.authorization;

import java.util.*;
import com.nigson.shared.security.ActorAssertion;

/** Commerce-owned repository port. All command methods run in one owner transaction. */
public interface StaffRepository {
    void serializeChanges();
    void lockParties(UUID actor, UUID target);
    void requireAdministrator(ActorAssertion.Actor actor);
    Staff.View find(UUID id);
    Staff.Page search(String q, int page, int size, String sort);
    long activeAdministrators();
    void replace(UUID id, Staff.Change change, Staff.View before, ActorAssertion.Actor actor, UUID correlation);
}
