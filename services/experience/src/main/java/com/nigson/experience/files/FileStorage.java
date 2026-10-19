package com.nigson.experience.files;

import java.time.*;
import java.util.*;

/** Real provider port; caller persists intent and releases transactions before HTTP. */
public interface FileStorage extends AutoCloseable {
    record ObjectInfo(String version,String etag,long size,String mimeType,String checksum,String owner,boolean encrypted) {}
    record Signed(String url,Map<String,String> headers,Instant expiresAt) { @Override public String toString(){return "Signed[protected]";} }
    boolean configured();
    Signed upload(UUID asset,String key,FileValues.Upload input,String owner,Instant issuedAt);
    ObjectInfo quarantine(String key);
    ObjectInfo finalObject(String key,boolean privateObject);
    void promote(String sourceKey,String sourceVersion,String sourceEtag,String finalKey,boolean privateObject);
    Signed download(String key,boolean privateObject,Instant issuedAt);
    @Override default void close(){}
}
