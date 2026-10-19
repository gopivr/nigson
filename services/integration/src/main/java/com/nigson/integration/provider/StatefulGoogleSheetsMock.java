package com.nigson.integration.provider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Stateful local-only Sheets mock. A configured one-shot fault models timeout-after-acceptance. */
public final class StatefulGoogleSheetsMock implements GoogleSheetsPort {
 public record Row(String contentHash,List<String> cells){public Row{cells=List.copyOf(cells);}@Override public String toString(){return "GoogleSheetRow[protected]";}}
 private record Key(String spreadsheet,String row){}
 private final Map<Key,Row> rows=new ConcurrentHashMap<>();private final AtomicBoolean timeoutAfterAccept=new AtomicBoolean();private final AtomicBoolean timeoutBeforeAccept=new AtomicBoolean();
 public void timeoutAfterNextAcceptedWrite(){timeoutAfterAccept.set(true);}public void timeoutBeforeNextWrite(){timeoutBeforeAccept.set(true);}
 public Optional<String> contentHash(String spreadsheetKey,String rowKey){return Optional.ofNullable(rows.get(new Key(spreadsheetKey,rowKey))).map(Row::contentHash);}
 public boolean safeToCreateAfterConfirmedAbsence(){return true;}
 public void upsert(String spreadsheetKey,String rowKey,CareerSheetRow row,String contentHash,String commandKey){if(TransactionActive.active())throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");if(spreadsheetKey==null||!spreadsheetKey.matches("[A-Za-z0-9_-]{1,120}")||rowKey==null||!rowKey.matches("[A-Za-z0-9:_-]{1,120}")||row==null||!row.applicationId().toString().equals(rowKey)||contentHash==null||!contentHash.matches("[a-f0-9]{64}")||commandKey==null||commandKey.length()>200)throw new IllegalArgumentException("SHEETS_REQUEST_INVALID");if(timeoutBeforeAccept.compareAndSet(true,false))throw new IllegalStateException("SHEETS_TIMEOUT_BEFORE_ACCEPT");rows.put(new Key(spreadsheetKey,rowKey),new Row(contentHash,row.cells()));if(timeoutAfterAccept.compareAndSet(true,false))throw new IllegalStateException("SHEETS_TIMEOUT_AFTER_ACCEPT");}
 public int rowCount(String spreadsheetKey){return (int)rows.keySet().stream().filter(k->k.spreadsheet().equals(spreadsheetKey)).count();}
 public Optional<Row> row(String spreadsheetKey,String rowKey){return Optional.ofNullable(rows.get(new Key(spreadsheetKey,rowKey)));}
 private static final class TransactionActive{static boolean active(){return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();}}
}
