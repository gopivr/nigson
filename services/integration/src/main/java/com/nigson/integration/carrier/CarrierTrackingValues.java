package com.nigson.integration.carrier;

import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Closed interpreted scan profile; carrier text, locations and recipient data are never retained. */
public final class CarrierTrackingValues {
 private CarrierTrackingValues(){}
 public record Scan(String tracking,String code,Instant occurredAt){}
 public record Observation(String status,Instant occurredAt){}
 public static List<Scan> parse(JsonNode output,List<String> expected,Instant now){
  try{if(expected==null||expected.isEmpty()||expected.size()>30||new HashSet<>(expected).size()!=expected.size())throw CarrierValues.rule();var complete=output.path("completeTrackResults");if(!complete.isArray()||complete.size()!=expected.size())throw CarrierValues.rule();var seen=new HashSet<String>();var scans=new ArrayList<Scan>();for(var group:complete){var results=group.path("trackResults");if(!results.isArray()||results.size()!=1||group.has("error"))throw CarrierValues.rule();var result=results.path(0);String id=result.path("trackingNumberInfo").path("trackingNumber").asString();if(!expected.contains(id)||!seen.add(id)||result.has("error"))throw CarrierValues.rule();String code=result.path("latestStatusDetail").path("code").asString();if(!Set.of("OC","PU","IT","OD","DL","CA","SE","DE").contains(code))throw CarrierValues.rule();var events=result.path("scanEvents");if(!events.isArray()||events.isEmpty()||events.size()>1000)throw CarrierValues.rule();Instant at=null;for(var event:events)if(code.equals(event.path("eventType").asString())){Instant candidate=Instant.parse(event.path("date").asString());if(at==null||candidate.isAfter(at))at=candidate;}if(at==null||at.isAfter(now.plusSeconds(5))||at.isBefore(Instant.EPOCH))throw CarrierValues.rule();if(code.equals("DL")){var dates=result.path("dateAndTimes");if(!dates.isArray()||dates.size()>100)throw CarrierValues.rule();Instant actual=null;for(var date:dates)if("ACTUAL_DELIVERY".equals(date.path("type").asString())){if(actual!=null)throw CarrierValues.rule();actual=Instant.parse(date.path("dateTime").asString());}if(actual==null||actual.isAfter(now.plusSeconds(5))||actual.isBefore(Instant.EPOCH))throw CarrierValues.rule();at=actual;}scans.add(new Scan(id,code,at));}return List.copyOf(scans);
  }catch(RuntimeException invalid){throw CarrierValues.rule();}
 }
 public static Observation aggregate(List<Scan> scans,int count){
  if(scans==null||scans.size()!=count||count<1||count>100||scans.stream().map(Scan::tracking).distinct().count()!=count)throw CarrierValues.rule();boolean cancelled=scans.stream().allMatch(s->s.code().equals("CA")),delivered=scans.stream().allMatch(s->s.code().equals("DL"));String status=cancelled?"CANCELLED":delivered?"DELIVERED":scans.stream().anyMatch(s->Set.of("CA","SE","DE").contains(s.code()))?"EXCEPTION":scans.stream().allMatch(s->Set.of("PU","IT","OD","DL").contains(s.code()))?"SHIPPED":null;
  return new Observation(status,scans.stream().filter(s->!"EXCEPTION".equals(status)||Set.of("CA","SE","DE").contains(s.code())).map(Scan::occurredAt).max(Comparator.naturalOrder()).orElseThrow());
 }
}
