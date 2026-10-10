package com.nigson.commerce.files;

import java.time.Instant;
import java.text.Normalizer;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.problem.*;
import com.nigson.shared.events.EventCodec;

/** Closed file contracts. Original filenames are display input only, never storage paths. */
public final class FileValues {
    public record Upload(String purpose,String filename,String mimeType,long size,String checksumSha256,UUID linkedResourceId,String extension) {}
    public record UploadResult(UUID assetId,String uploadUrl,Map<String,String> requiredHeaders,Instant expiresAt) {
        public UploadResult { requiredHeaders=Collections.unmodifiableMap(new TreeMap<>(requiredHeaders)); }
        @Override public String toString(){return "UploadResult[protected]";}
    }
    public record Asset(UUID id,String state,String purpose,String mimeType,long size,String checksum,String url,long version) {}
    public record Download(String url,Instant expiresAt) { @Override public String toString(){return "Download[protected]";} }
    public record Scan(UUID commandId,String checksumSha256,String result,String scannerVersion,String reasonCode) {}
    private FileValues(){}
    public static JsonNode object(JsonNode input,Set<String> fields){
        if(input==null||!input.isObject()||EventCodec.JSON.writeValueAsBytes(input).length>8192||!fields.containsAll(input.properties().stream().map(Map.Entry::getKey).toList()))throw ApiValues.invalid();return input;
    }
    public static String text(JsonNode input,String field,int max){
        var v=input.get(field);if(v==null||!v.isString())throw ApiValues.invalid();String text=Normalizer.normalize(v.asString(),Normalizer.Form.NFC);
        if(text.isBlank()||text.length()>max||text.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw ApiValues.invalid();return text;
    }
    public static String checksum(JsonNode input){String v=text(input,"checksumSha256",64);if(!v.matches("[a-f0-9]{64}"))throw ApiValues.invalid();return v;}
    public static Upload upload(JsonNode input){
        object(input,Set.of("purpose","filename","mimeType","size","checksumSha256","linkedResourceId"));
        String purpose=text(input,"purpose",20),filename=text(input,"filename",120),mime=text(input,"mimeType",100);
        if(filename.contains("/")||filename.contains("\\")||filename.equals(".")||filename.equals(".."))throw ApiValues.invalid();
        var extension=switch(mime){case "image/jpeg"->"jpg";case "image/png"->"png";case "image/webp"->"webp";case "application/pdf"->"pdf";default->throw rule();};
        long maximum=switch(purpose){case "IMAGE"->{if(extension.equals("pdf"))throw rule();yield 5L*1024*1024;}case "BROCHURE"->{if(!extension.equals("pdf"))throw rule();yield 10L*1024*1024;}default->throw rule();};
        var size=input.get("size");if(size==null||!size.isIntegralNumber()||!size.canConvertToLong()||size.asLong()<1||size.asLong()>maximum)throw ApiValues.invalid();
        UUID linked=null;if(input.has("linkedResourceId")){var value=input.get("linkedResourceId");if(!value.isString())throw ApiValues.invalid();linked=ApiValues.uuid(value.asString());}
        return new Upload(purpose,filename,mime,size.asLong(),checksum(input),linked,extension);
    }
    public static String completion(JsonNode input){object(input,Set.of("checksumSha256"));if(input.size()!=1)throw ApiValues.invalid();return checksum(input);}
    public static Scan scan(JsonNode input){
        object(input,Set.of("commandId","checksumSha256","result","scannerVersion","reasonCode"));
        var command=ApiValues.uuid(text(input,"commandId",36));String result=text(input,"result",8),scanner=text(input,"scannerVersion",100);
        if(!Set.of("CLEAN","REJECTED").contains(result)||!scanner.matches("[A-Za-z0-9][A-Za-z0-9._:+-]{0,99}"))throw ApiValues.invalid();
        String reason=input.has("reasonCode")?text(input,"reasonCode",100):null;if(reason!=null&&!reason.matches("[A-Z][A-Z0-9_]{0,99}"))throw ApiValues.invalid();
        return new Scan(command,checksum(input),result,scanner,reason);
    }
    public static String key(String env,String purpose,UUID asset,long revision,String extension){
        if(!Set.of("local","test","dev","qa","stage","prod").contains(env)||!Set.of("IMAGE","BROCHURE","SHIPPING_LABEL").contains(purpose)||asset==null||asset.equals(new UUID(0,0))||revision<0||!Set.of("jpg","png","webp","pdf").contains(extension))throw ApiValues.invalid();
        return env+"/commerce/"+purpose.toLowerCase(Locale.ROOT)+"/"+asset+"/"+revision+"/"+extension;
    }
    public static Problem.Failure rule(){return new Problem.Failure(ProblemCode.FILES_RULE_VIOLATION);}
}
