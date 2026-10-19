package com.nigson.integration.provider;

import java.util.*;

/** Owner adapter contract uses a stable row key so retries update, never append blindly. */
public interface GoogleSheetsPort {
 Optional<String> contentHash(String spreadsheetKey,String rowKey);
 void upsert(String spreadsheetKey,String rowKey,CareerSheetRow row,String contentHash,String commandKey);
 /** True only when retrying a confirmed-absent row uses an idempotent keyed upsert. */
 default boolean safeToCreateAfterConfirmedAbsence(){return false;}
}
