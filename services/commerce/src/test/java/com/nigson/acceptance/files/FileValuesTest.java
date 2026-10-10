package com.nigson.acceptance.files;

import com.nigson.commerce.files.FileValues;
import com.nigson.shared.events.EventCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FileValuesTest {
    Map<String,Object> input(){return new HashMap<>(Map.of("purpose","IMAGE","filename","Synthetic image.png","mimeType","image/png","size",128,"checksumSha256","a".repeat(64)));}
    @Test void acceptedPurposeLimitsAndImmutableKeyNeverContainFilename(){
        var image=FileValues.upload(EventCodec.JSON.valueToTree(input()));assertThat(image.extension()).isEqualTo("png");UUID asset=UUID.randomUUID();
        assertThat(FileValues.key("test",image.purpose(),asset,0,image.extension())).isEqualTo("test/commerce/image/"+asset+"/0/png");
        var v=input();v.put("purpose","BROCHURE");v.put("mimeType","application/pdf");v.put("size",10*1024*1024);assertThat(FileValues.upload(EventCodec.JSON.valueToTree(v)).extension()).isEqualTo("pdf");
    }
    @Test void exactTypesPurposeAndPathInjectionAreRejected(){
        for(var change:List.of(Map.of("purpose","SHIPPING_LABEL"),Map.of("purpose","CV"),Map.of("mimeType","image/svg+xml"),Map.of("mimeType","application/pdf"),Map.of("filename","../a.png"),Map.of("filename","a\u202eb.png"),Map.of("size","128"),Map.of("size",0),Map.of("size",5*1024*1024+1),Map.of("checksumSha256","A".repeat(64)),Map.of("linkedResourceId","00000000-0000-0000-0000-000000000000"),Map.of("classification","PRIVATE"))){var v=input();v.putAll(change);assertThatThrownBy(()->FileValues.upload(EventCodec.JSON.valueToTree(v))).isInstanceOf(RuntimeException.class);}
        assertThatThrownBy(()->FileValues.completion(EventCodec.JSON.readTree("{\"checksumSha256\":\""+"a".repeat(64)+"\",\"result\":\"CLEAN\"}"))).isInstanceOf(RuntimeException.class);
    }
    @Test void callbackIsClosedAndScannerFieldsCannotCarryArbitraryContent(){
        var v=new HashMap<String,Object>(Map.of("commandId",UUID.randomUUID(),"checksumSha256","a".repeat(64),"result","CLEAN","scannerVersion","clamav:1.5.0+db123"));
        assertThat(FileValues.scan(EventCodec.JSON.valueToTree(v)).result()).isEqualTo("CLEAN");
        v.put("reasonCode","<script>");assertThatThrownBy(()->FileValues.scan(EventCodec.JSON.valueToTree(v))).isInstanceOf(RuntimeException.class);
        v.put("reasonCode","MALWARE_FOUND");v.put("result","PUBLISHED");assertThatThrownBy(()->FileValues.scan(EventCodec.JSON.valueToTree(v))).isInstanceOf(RuntimeException.class);
    }
}
