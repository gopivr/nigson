package com.nigson.experience.reporting;

public interface ReportRepository {
    ReportValues.Summary engagement(ReportValues.Query query);
}
