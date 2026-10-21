package com.nigson.experience.files;

import java.time.Instant;
import java.util.UUID;

public interface FileRepository {
    record Stored(UUID id, UUID owner, UUID organization, String key, String purpose, String mime,
                  long size, String checksum, String classification, String status, String linkedResourceType, UUID linkedResourceId, long version,
                  String sourceKey, String finalKey, Instant uploadExpires, UUID scanCommand,
                  String sourceVersion, String sourceEtag, String transferState, Instant lease,
                  String scanHash, String scanResult, String scannerVersion) {}
    Stored find(UUID asset, boolean lock);
    Stored create(UUID asset, UUID owner, UUID organization, FileValues.Upload input,
                  String source, String target, Instant expires, String actor);
    Stored quarantined(Stored before, FileStorage.ObjectInfo info, UUID command, String actor);
    Stored claim(Stored before, FileValues.Scan scan, String requestHash, Instant lease, String actor);
    Stored unknown(Stored before, String requestHash, String actor);
    Stored finish(Stored before, String result, String requestHash, String scannerVersion, String actor);
}
