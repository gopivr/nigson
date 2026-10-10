package com.nigson.commerce.notification;
import java.util.UUID;
public interface TemplatePort {
    boolean configured();
    record Accepted(UUID id,String code,int revision,boolean active,Long version){}
    Accepted put(TemplateValues.Template template,Long version,String command,String actor,UUID correlation);
}
