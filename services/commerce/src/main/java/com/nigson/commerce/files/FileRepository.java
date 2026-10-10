package com.nigson.commerce.files;

import java.time.Instant;
import java.util.UUID;
import com.nigson.shared.security.ActorAssertion;

/** Owned metadata/transfer port. No binary bytes or external calls in local transactions. */
public interface FileRepository {
    record Stored(UUID id,UUID owner,UUID product,UUID sku,UUID shipment,String key,String purpose,String mime,long size,String checksum,String state,String classification,long version,Instant createdAt,String sourceKey,String finalKey,Instant uploadExpires,UUID scanCommand,String sourceVersion,String sourceEtag,String transferState,Instant lease,String scanHash,String scanResult,String scannerVersion) {}
    record Link(UUID product,UUID sku) {}
    void bound();
    void authorize(ActorAssertion.Actor actor,String role,boolean lock);
    Link linked(UUID resource,boolean lock);
    Stored find(UUID asset,boolean lock);
    void access(ActorAssertion.Actor actor,Stored asset,boolean lock);
    Stored create(UUID asset,ActorAssertion.Actor actor,FileValues.Upload input,String source,String target,Instant expiry);
    Stored quarantined(Stored before,FileStorage.ObjectInfo verified,UUID scanCommand,String actor);
    Stored claim(Stored before,FileValues.Scan input,String hash,Instant lease,String actor);
    void unknown(UUID asset,String hash,String actor);
    Stored finish(Stored before,String state,String actor);
}
