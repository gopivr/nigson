package com.nigson.experience.lead;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.nigson.shared.observability.WireContext;
import com.nigson.shared.problem.ApiValues;

@RestController
public final class InboundMessageController {
    private final InboundMessageProcessor processor;
    public InboundMessageController(InboundMessageProcessor processor){this.processor=processor;}
    @PostMapping(value="/api/v1/webhooks/notifications/twilio/whatsapp",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> inbound(HttpServletRequest request){
        String content=request.getContentType();if(request.getQueryString()!=null||content==null||!"application/x-www-form-urlencoded".equalsIgnoreCase(content.split(";",2)[0]))throw ApiValues.invalid();
        Map<String,String> form=new LinkedHashMap<>();
        try {String wire=new String(request.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(wire.isEmpty()||wire.length()>262144)throw ApiValues.invalid();for(String pair:wire.split("&",-1)){if(pair.isEmpty()||form.size()>=100)throw ApiValues.invalid();int separator=pair.indexOf('=');if(separator<1)throw ApiValues.invalid();String key=java.net.URLDecoder.decode(pair.substring(0,separator),java.nio.charset.StandardCharsets.UTF_8),value=java.net.URLDecoder.decode(pair.substring(separator+1),java.nio.charset.StandardCharsets.UTF_8);if(key.length()>100||value.length()>4096||form.putIfAbsent(key,value)!=null)throw ApiValues.invalid();}}
        catch(java.io.IOException|IllegalArgumentException invalid){throw ApiValues.invalid();}
        String signature=request.getHeader("X-Twilio-Signature");var current=WireContext.current();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(processor.accept(signature,form,current==null?WireContext.correlation(null):current.correlationId));
    }
}
