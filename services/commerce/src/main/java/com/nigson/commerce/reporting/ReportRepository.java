package com.nigson.commerce.reporting;

import com.nigson.shared.security.ActorAssertion;

public interface ReportRepository {
    ReportValues.Summary commerce(ActorAssertion.Actor actor, ReportValues.Query query);
}
