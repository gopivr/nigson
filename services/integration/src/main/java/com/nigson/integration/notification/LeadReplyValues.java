package com.nigson.integration.notification;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.ApiValues;
import tools.jackson.databind.JsonNode;

/** Closed API-193 command DTO; variables remain protected in diagnostics. */
public final class LeadReplyValues {
    private LeadReplyValues(){}
    public record Command(UUID commandId,UUID leadId,String templateCode,String channel,Map<String,String> variables){
        public Command{if(commandId==null||commandId.equals(new UUID(0,0))||leadId==null||leadId.equals(new UUID(0,0))||!"STAFF_LEAD_REPLY".equals(templateCode)||!"WHATSAPP".equals(channel)||variables==null||variables.size()>20)throw ApiValues.invalid();variables=Map.copyOf(variables);}
        @Override public String toString(){return "LeadReplyCommand[protected]";}
    }
    public record Response(UUID jobId,String state){public Response{if(jobId==null||jobId.equals(new UUID(0,0))||!"PENDING".equals(state))throw ApiValues.invalid();}}
    public static Command command(JsonNode node){
        if(node==null||!node.isObject()||!node.propertyNames().equals(Set.of("commandId","leadId","templateCode","channel","variables"))||!node.path("commandId").isString()||!node.path("leadId").isString()||!node.path("templateCode").isString()||!node.path("channel").isString()||!node.path("variables").isObject()||node.path("variables").size()>20||EventCodec.JSON.writeValueAsBytes(node).length>8192)throw ApiValues.invalid();
        var values=new TreeMap<String,String>();for(var property:node.path("variables").properties()){String key=property.getKey();var value=property.getValue();if(!key.matches("[A-Za-z0-9_]{1,40}")||!value.isString()||value.asString().length()>1000||value.asString().codePoints().anyMatch(c->Character.isISOControl(c)))throw ApiValues.invalid();values.put(key,value.asString());}
        try{return new Command(ApiValues.uuid(node.path("commandId").asString()),ApiValues.uuid(node.path("leadId").asString()),node.path("templateCode").asString(),node.path("channel").asString(),values);}catch(RuntimeException invalid){throw ApiValues.invalid();}
    }
    public static byte[] canonical(Command c){var node=EventCodec.JSON.createObjectNode();node.put("commandId",c.commandId().toString());node.put("leadId",c.leadId().toString());node.put("templateCode",c.templateCode());node.put("channel",c.channel());node.set("variables",EventCodec.JSON.valueToTree(new TreeMap<>(c.variables())));return EventCodec.JSON.writeValueAsBytes(node);}
}
