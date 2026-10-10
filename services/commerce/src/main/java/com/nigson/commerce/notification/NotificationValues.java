package com.nigson.commerce.notification;

import java.net.URI;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;

/** Minimal owner contracts; never a customer/entity dump or permission to send. */
public final class NotificationValues {
    private NotificationValues(){}
    public record Recipient(UUID partyId,String email){@Override public String toString(){return "Recipient[protected]";}}
    public record Context(String orderNumber,Recipient recipient,String total,String currency,String trackingUrl){@Override public String toString(){return "NotificationContext[protected]";}}
    public record Delivery(UUID jobId,UUID resourceId,String state,String providerReference){@Override public String toString(){return "Delivery[protected]";}}
    public record Acknowledged(boolean acknowledged){}
    public static Delivery delivery(JsonNode input){
        if(input==null||!input.isObject()||EventCodec.JSON.writeValueAsBytes(input).length>2048||!Set.of("jobId","resourceId","state","providerReference").containsAll(input.propertyNames()))throw ApiValues.invalid();
        for(String key:List.of("jobId","resourceId","state"))if(!input.has(key)||!input.get(key).isString())throw ApiValues.invalid();
        String state=input.get("state").asString();if(!Set.of("SENT","FAILED","SUPPRESSED","UNKNOWN").contains(state))throw ApiValues.invalid();String reference=null;
        if(input.has("providerReference")){var n=input.get("providerReference");if(!n.isString()||!n.asString().matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,159}"))throw ApiValues.invalid();reference=n.asString();}
        return new Delivery(ApiValues.uuid(input.get("jobId").asString()),ApiValues.uuid(input.get("resourceId").asString()),state,reference);
    }
    public static String tracking(String value){if(value==null)return null;try{URI uri=URI.create(value);if(value.length()>1000||!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1&&uri.getPort()!=443||value.codePoints().anyMatch(Character::isISOControl))throw ApiValues.invalid();return value;}catch(IllegalArgumentException invalid){throw ApiValues.invalid();}}
}
