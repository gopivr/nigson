package com.nigson.commerce.provider;
import java.util.UUID;
/** Owned proxy port; integration alone owns job persistence and provider reconciliation. */
public interface IntegrationJobs {
    boolean configured();
    JobValues.Page list(JobValues.Query query,UUID correlation);
    record Accepted(JobValues.Job job,long version){}
    Accepted retry(UUID job,long version,JobValues.Retry body,String commandKey,String actor,UUID correlation);
}
