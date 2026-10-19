package com.nigson.integration.provider;

import java.util.*;
import com.nigson.shared.problem.ApiValues;
import com.nigson.shared.events.EventCodec;

/** Minimized, deterministic Sheet row. Values are never included in diagnostics. */
public record CareerSheetRow(UUID applicationId,String opportunityTitle,String applicantName,String applicantEmail,String submittedAt,String state,SortedMap<String,String> approvedAnswers){
 public CareerSheetRow{if(applicationId==null||applicationId.equals(new UUID(0,0))||opportunityTitle==null||opportunityTitle.isBlank()||opportunityTitle.length()>200||applicantName==null||applicantName.isBlank()||applicantName.length()>200||applicantEmail==null||applicantEmail.length()>254||submittedAt==null||submittedAt.length()>40||state==null||!Set.of("SUBMITTED","REVIEWED","SHORTLISTED","REJECTED","CLOSED").contains(state)||approvedAnswers==null||approvedAnswers.size()>32)throw ApiValues.invalid();approvedAnswers=Collections.unmodifiableSortedMap(new TreeMap<>(approvedAnswers));for(var entry:approvedAnswers.entrySet())if(entry.getKey()==null||!entry.getKey().matches("[a-z][a-zA-Z0-9]{0,39}")||entry.getValue()==null||entry.getValue().length()>2000)throw ApiValues.invalid();}
 public List<String> cells(){var values=new ArrayList<String>();values.add(applicationId.toString());values.add(opportunityTitle);values.add(applicantName);values.add(applicantEmail);values.add(submittedAt);values.add(state);values.add(EventCodec.JSON.writeValueAsString(approvedAnswers));return values.stream().map(CareerSheetRow::safeCell).toList();}
 private static String safeCell(String raw){String v=raw.replace("\u0000","");int first=0;while(first<v.length()&&Character.isWhitespace(v.charAt(first)))first++;if(first<v.length()&&"=+-@".indexOf(v.charAt(first))>=0)return "'"+v;return v;}
 @Override public String toString(){return "CareerSheetRow[protected]";}
}
