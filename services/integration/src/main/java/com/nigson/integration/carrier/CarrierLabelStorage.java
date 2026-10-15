package com.nigson.integration.carrier;

import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.nigson.shared.problem.*;

/** Conditional immutable private label writes; a response loss is resolved by checksum/version HEAD. */
public final class CarrierLabelStorage {
 public record Stored(String key,String checksum,long bytes,String version,String etag){@Override public String toString(){return "StoredCarrierLabel[protected]";}}
 private final S3Client client;private final String environment,bucket,kms;
 public CarrierLabelStorage(S3Client client,String environment,String bucket,String kms){if(environment==null||!Set.of("local","test","dev","qa","stage","prod").contains(environment)||bucket==null||!bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")||bucket.matches("[0-9-]+")||kms==null||Set.of("local","test").contains(environment)&&!kms.isEmpty()||!Set.of("local","test").contains(environment)&&!kms.matches("arn:aws(?:-us-gov|-cn)?:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]{36}"))throw new IllegalArgumentException("CARRIER_PRIVATE_STORAGE_REQUIRED");this.client=Objects.requireNonNull(client);this.environment=environment;this.bucket=bucket;this.kms=kms;}
 public Stored store(UUID shipment,UUID booking,CarrierLabelCodec.Label label){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");for(UUID id:Arrays.asList(shipment,booking))if(id==null||id.equals(new UUID(0,0)))throw ApiValues.invalid();Objects.requireNonNull(label);String key=environment+"/commerce/shipping_label/"+shipment+"/1/pdf";var existing=head(key,booking,label.checksum(),label.bytes().length);if(existing!=null)return existing;
  try{var request=PutObjectRequest.builder().bucket(bucket).key(key).ifNoneMatch("*").contentType("application/pdf").contentLength((long)label.bytes().length).checksumSHA256(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(label.checksum()))).metadata(Map.of("owner",booking.toString(),"asset",shipment.toString()));if(kms.isEmpty())request.serverSideEncryption(ServerSideEncryption.AES256);else request.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(kms);client.putObject(request.build(),RequestBody.fromBytes(label.bytes()));}catch(software.amazon.awssdk.core.exception.SdkException uncertain){var recovered=head(key,booking,label.checksum(),label.bytes().length);if(recovered!=null)return recovered;throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);}
  var stored=head(key,booking,label.checksum(),label.bytes().length);if(stored==null)throw new Problem.Failure(ProblemCode.PROVIDER_OUTCOME_UNKNOWN);return stored;
 }
 public String key(UUID shipment){if(shipment==null||shipment.equals(new UUID(0,0)))throw ApiValues.invalid();return environment+"/commerce/shipping_label/"+shipment+"/1/pdf";}
 public Stored recover(UUID shipment,UUID booking,String checksum,long bytes){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");if(booking==null||booking.equals(new UUID(0,0))||checksum==null||!checksum.matches("[a-f0-9]{64}")||bytes<1||bytes>10*1024*1024)throw CarrierValues.rule();return head(key(shipment),booking,checksum,bytes);}
 private Stored head(String key,UUID booking,String expectedChecksum,long expectedSize){
  HeadObjectResponse response;try{response=client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).checksumMode(ChecksumMode.ENABLED).build());}catch(S3Exception failure){if(failure.statusCode()==404)return null;throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}catch(software.amazon.awssdk.core.exception.SdkException unavailable){throw new Problem.Failure(ProblemCode.PROVIDER_UNAVAILABLE);}
  try{String checksum=HexFormat.of().formatHex(Base64.getDecoder().decode(response.checksumSHA256()));if(!checksum.equals(expectedChecksum)||!booking.toString().equals(response.metadata().get("owner"))||!"application/pdf".equals(response.contentType())||response.contentLength()==null||response.contentLength()!=expectedSize||response.versionId()==null||response.versionId().isBlank()||response.versionId().equals("null")||response.eTag()==null||response.eTag().isBlank()||!(kms.isEmpty()?response.serverSideEncryption()==ServerSideEncryption.AES256:response.serverSideEncryption()==ServerSideEncryption.AWS_KMS&&kms.equals(response.ssekmsKeyId())))throw CarrierValues.rule();return new Stored(key,checksum,response.contentLength(),response.versionId(),response.eTag());}catch(Problem.Failure safe){throw safe;}catch(RuntimeException invalid){throw CarrierValues.rule();}
 }
}
