package com.nigson.integration.provider;

import java.util.*;
import com.nigson.shared.problem.*;
/** Approved owner adapters interpret their contracts; execution/reconciliation always occur outside SQL. */
public interface JobHandler {
 enum Provider { COMMERCE_PAYMENT,FEDEX,GOOGLE_SHEETS,S3,COMMERCE_REPORTS }
 record Outcome(JobValues.State state,String nextCursor,ProblemCode error){
  public Outcome{if(state==null||!Set.of(JobValues.State.PENDING,JobValues.State.SUCCEEDED,JobValues.State.FAILED,JobValues.State.UNKNOWN).contains(state)||nextCursor!=null&&!nextCursor.matches("[A-Za-z0-9_-]{1,256}")||state==JobValues.State.PENDING&&nextCursor==null||state!=JobValues.State.PENDING&&nextCursor!=null||Set.of(JobValues.State.FAILED,JobValues.State.UNKNOWN).contains(state)&&error==null||Set.of(JobValues.State.SUCCEEDED,JobValues.State.PENDING).contains(state)&&error!=null)throw ApiValues.invalid();}
 }
 JobValues.Kind kind();
 Provider provider();
 Outcome execute(JobExecution.Claim claim);
 /** Implement authoritative lookup/ack recovery; never blindly repeat an uncertain provider write. */
 Outcome reconcile(JobExecution.Claim claim);
}
