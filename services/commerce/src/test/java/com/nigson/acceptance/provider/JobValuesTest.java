package com.nigson.acceptance.provider;
import com.nigson.commerce.provider.*;
import com.nigson.shared.events.EventCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class JobValuesTest {
    @Test void rejectsUnknownFieldsTypesControlCharactersAndUnboundedInputs(){
        assertThat(JobValues.retry(EventCodec.JSON.readTree("{\"reason\":\"Synthetic operator retry\"}")).toString()).doesNotContain("Synthetic");
        for(String input:List.of("{}","{\"reason\":false}","{\"reason\":\"\"}","{\"reason\":\"a\\nb\"}","{\"reason\":\"a\",\"url\":\"https://example.invalid\"}","{\"reason\":\""+"x".repeat(501)+"\"}"))assertThatThrownBy(()->JobValues.retry(EventCodec.JSON.readTree(input))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(()->new JobValues.Query("IN_PROGRESS",0,20,"id,asc")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new JobValues.Query(null,0,101,"id,asc")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->new JobValues.Query(null,0,20,"secret,asc")).hasMessage("VALIDATION_ERROR");
    }
    @Test void enforcesClosedJobAndPageIncludingSafeErrorCodeAndDuplicateIdentity(){
        var job=EventCodec.JSON.readTree("{\"id\":\""+UUID.randomUUID()+"\",\"state\":\"FAILED\",\"attemptCount\":1,\"nextAttemptAt\":\"2026-10-10T00:00:00Z\",\"lastErrorCode\":null}");assertThat(JobValues.job(job).state()).isEqualTo("FAILED");
        for(String field:List.of("businessKey","providerKey","email","payload")){var changed=(tools.jackson.databind.node.ObjectNode)job.deepCopy();changed.put(field,"protected");assertThatThrownBy(()->JobValues.job(changed)).hasMessage("VALIDATION_ERROR");}
        var error=(tools.jackson.databind.node.ObjectNode)job.deepCopy();error.put("lastErrorCode","https://protected.invalid/customer");assertThatThrownBy(()->JobValues.job(error)).hasMessage("VALIDATION_ERROR");
        var page=EventCodec.JSON.createObjectNode().put("page",0).put("size",20).put("totalElements",1).put("totalPages",1);page.set("items",EventCodec.JSON.valueToTree(List.of(job)));assertThat(JobValues.page(page,new JobValues.Query(null,0,20,"id,asc")).items()).hasSize(1);
        page.put("totalElements",2);page.set("items",EventCodec.JSON.valueToTree(List.of(job,job)));assertThatThrownBy(()->JobValues.page(page,new JobValues.Query(null,0,20,"id,asc"))).hasMessage("VALIDATION_ERROR");
    }
}
