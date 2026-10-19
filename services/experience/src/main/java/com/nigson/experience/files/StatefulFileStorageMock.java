package com.nigson.experience.files;

import java.time.*;import java.util.*;import java.util.concurrent.*;import org.springframework.transaction.support.TransactionSynchronizationManager;import com.nigson.shared.problem.*;
/** Explicit local/test S3 stand-in. Upload, HEAD, promotion and uncertainty are stateful. */
public final class StatefulFileStorageMock implements FileStorage {
 private final ConcurrentMap<String,ObjectInfo> quarantine=new ConcurrentHashMap<>(),finals=new ConcurrentHashMap<>();private volatile boolean copyTimeoutAfterAccept,copyUnavailable;
 private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_HTTP_IN_TRANSACTION");}
 public boolean configured(){return true;} public void failCopyAfterAcceptance(){copyTimeoutAfterAccept=true;}public void failCopyBeforeAcceptance(){copyUnavailable=true;}
 public Signed upload(UUID id,String key,FileValues.Upload in,String owner,Instant issued){outside();String version=UUID.randomUUID().toString(),etag=UUID.randomUUID().toString();quarantine.putIfAbsent(key,new ObjectInfo(version,etag,in.size(),in.mimeType(),in.checksumSha256(),owner,true));return new Signed("https://s3-mock.invalid/upload/"+id,Map.of("content-type",in.mimeType(),"x-checksum-sha256",in.checksumSha256()),issued.plusSeconds(300));}
 public ObjectInfo quarantine(String key){outside();return quarantine.get(key);}public ObjectInfo finalObject(String key,boolean secret){outside();return finals.get(key);}
 public void promote(String source,String version,String etag,String target,boolean secret){outside();if(copyUnavailable){copyUnavailable=false;throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}ObjectInfo src=quarantine.get(source);if(src==null||!src.version().equals(version)||!src.etag().equals(etag))throw FileValues.rule();finals.putIfAbsent(target,src);if(copyTimeoutAfterAccept){copyTimeoutAfterAccept=false;throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}}
 public Signed download(String key,boolean secret,Instant issued){outside();if(!finals.containsKey(key))throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);return new Signed("https://s3-mock.invalid/download/"+UUID.randomUUID(),Map.of(),issued.plusSeconds(60));}
}
