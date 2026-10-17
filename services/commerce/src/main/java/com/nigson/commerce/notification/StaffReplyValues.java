package com.nigson.commerce.notification;

import java.util.*;
import com.nigson.shared.problem.ApiValues;
import tools.jackson.databind.JsonNode;

/** Closed browser API-192 values. */
public final class StaffReplyValues {
    private StaffReplyValues(){}
    public record Command(UUID commandId,UUID leadId,String templateCode,String channel,Map<String,String> variables){
        public Command{if(commandId==null||commandId.equals(new UUID(0,0))||leadId==null||leadId.equals(new UUID(0,0))||!"STAFF_LEAD_REPLY".equals(templateCode)||!"WHATSAPP".equals(channel)||variables==null||variables.size()>20)throw ApiValues.invalid();variables=Map.copyOf(variables);}
        @Override public String toString(){return "StaffReplyCommand[protected]";}
    }
    public record Acknowledgement(UUID jobId,String state){public Acknowledgement{if(jobId==null||jobId.equals(new UUID(0,0))||!"PENDING".equals(state))throw ApiValues.invalid();}}
    public static Command parse(JsonNode node,UUID lead,UUID command){if(node==null||!node.isObject()||!node.propertyNames().equals(Set.of("templateCode","channel","variables"))||!node.path("templateCode").isString()||!node.path("channel").isString()||!node.path("variables").isObject()||node.path("variables").size()>20)throw ApiValues.invalid();var vars=new TreeMap<String,String>();for(var entry:node.path("variables").properties()){if(!entry.getKey().matches("[A-Za-z0-9_]{1,40}")||!entry.getValue().isString()||entry.getValue().asString().length()>1000||entry.getValue().asString().codePoints().anyMatch(Character::isISOControl))throw ApiValues.invalid();vars.put(entry.getKey(),entry.getValue().asString());}return new Command(command,lead,node.path("templateCode").asString(),node.path("channel").asString(),vars);}
}
