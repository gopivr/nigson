package com.nigson.experience.files;

import java.time.*;
import java.util.*;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.nigson.shared.problem.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Pinned S3 SDK adapter. Every provider operation is forbidden inside a DB transaction. */
public final class S3FileStorage implements FileStorage {
    private final S3Client s3;private final S3Presigner signer;private final String quarantine,publicBucket,privateBucket,kms;private final Clock clock;
    public S3FileStorage(S3Client s3,S3Presigner signer,String quarantine,String publicBucket,String privateBucket,String kms,Clock clock){this.s3=s3;this.signer=signer;this.quarantine=quarantine;this.publicBucket=publicBucket;this.privateBucket=privateBucket;this.kms=kms;this.clock=clock;}
    public boolean configured(){return true;}
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_HTTP_IN_TRANSACTION");}
    private String bucket(boolean secret){return secret?privateBucket:publicBucket;}
    private Map<String,String> headers(Map<String,List<String>> signed){var result=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);signed.forEach((k,v)->{if(!k.equalsIgnoreCase("host")){if(v.size()!=1)throw FileValues.rule();result.put(k,v.getFirst());}});return Collections.unmodifiableMap(result);}
    private void key(String key){if(key==null||key.length()>500||!key.matches("(?:local|test|dev|qa|stage|prod)/experience/(?:image|brochure|distributor_proof)/[a-f0-9-]{36}/[0-9]+/(?:jpg|png|webp|pdf)"))throw FileValues.rule();}
    private void deadline(Instant issued,Duration duration){if(issued==null||issued.isAfter(clock.instant().plusSeconds(5))||!issued.plus(duration).isAfter(clock.instant()))throw new Problem.Failure(ProblemCode.INVALID_TRANSITION);}
    public Signed upload(UUID asset,String key,FileValues.Upload input,String owner,Instant issued){
        outside();key(key);deadline(issued,Duration.ofMinutes(5));
        var put=PutObjectRequest.builder().bucket(quarantine).key(key).contentType(input.mimeType()).contentLength(input.size()).checksumSHA256(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(input.checksumSha256()))).metadata(Map.of("owner",owner,"asset",asset.toString())).ifNoneMatch("*").serverSideEncryption(kms.isBlank()?ServerSideEncryption.AES256:ServerSideEncryption.AWS_KMS);
        if(!kms.isBlank())put.ssekmsKeyId(kms);
        var result=signer.presignPutObject(p->p.signatureDuration(Duration.between(clock.instant(),issued.plusSeconds(300))).putObjectRequest(put.build()));
        if(!result.url().getProtocol().equals("https"))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);
        return new Signed(result.url().toString(),headers(result.signedHeaders()),issued.plusSeconds(300));
    }
    private ObjectInfo head(String bucket,String key){
        outside();key(key);
        try{var response=s3.headObject(r->r.bucket(bucket).key(key).checksumMode(ChecksumMode.ENABLED));
            String checksum=response.checksumSHA256();if(checksum==null)throw FileValues.rule();String hex;try{hex=HexFormat.of().formatHex(Base64.getDecoder().decode(checksum));}catch(IllegalArgumentException bad){throw FileValues.rule();}
            if(!hex.matches("[a-f0-9]{64}")||response.versionId()==null||response.versionId().equals("null")||response.versionId().length()>200||response.eTag()==null||response.eTag().length()>200)throw FileValues.rule();
            return new ObjectInfo(response.versionId(),response.eTag(),response.contentLength(),response.contentType(),hex,response.metadata().get("owner"),response.serverSideEncryption()!=null&&(kms.isBlank()||kms.equals(response.ssekmsKeyId())));
        }catch(S3Exception e){if(e.statusCode()==404)return null;throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}catch(software.amazon.awssdk.core.exception.SdkClientException e){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
    }
    public ObjectInfo quarantine(String key){return head(quarantine,key);}
    public ObjectInfo finalObject(String key,boolean secret){return head(bucket(secret),key);}
    public void promote(String source,String version,String etag,String target,boolean secret){
        outside();key(source);key(target);if(version==null||version.isBlank()||version.length()>200||etag==null||etag.length()>200)throw FileValues.rule();
        var copy=CopyObjectRequest.builder().sourceBucket(quarantine).sourceKey(source).sourceVersionId(version).copySourceIfMatch(etag).destinationBucket(bucket(secret)).destinationKey(target).ifNoneMatch("*").metadataDirective(MetadataDirective.COPY).checksumAlgorithm(ChecksumAlgorithm.SHA256).serverSideEncryption(kms.isBlank()?ServerSideEncryption.AES256:ServerSideEncryption.AWS_KMS);
        if(!kms.isBlank())copy.ssekmsKeyId(kms);
        try{s3.copyObject(copy.build());}catch(S3Exception e){if(e.statusCode()==412)throw new Problem.Failure(ProblemCode.CONFLICT);throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}catch(software.amazon.awssdk.core.exception.SdkClientException e){throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
    }
    public Signed download(String key,boolean secret,Instant issued){
        outside();key(key);deadline(issued,Duration.ofSeconds(60));
        var result=signer.presignGetObject(p->p.signatureDuration(Duration.ofSeconds(60)).getObjectRequest(r->r.bucket(bucket(secret)).key(key).responseContentDisposition("attachment").responseCacheControl("no-store")));
        if(!result.url().getProtocol().equals("https"))throw new Problem.Failure(ProblemCode.PROVIDER_MODE_MISMATCH);
        return new Signed(result.url().toString(),Map.of(),issued.plusSeconds(60));
    }
    @Override public void close(){signer.close();s3.close();}
}
