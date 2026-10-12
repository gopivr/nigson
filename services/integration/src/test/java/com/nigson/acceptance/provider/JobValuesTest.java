package com.nigson.acceptance.provider;
import com.nigson.integration.provider.JobValues;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class JobValuesTest {
 @Test void nonLocalFoundationRequiresPrivateSecurity(){
  assertThatThrownBy(()->new com.nigson.integration.bootstrap.internal.FoundationConfiguration("prod",false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new com.nigson.integration.bootstrap.internal.FoundationConfiguration("unrecognized",true)).isInstanceOf(IllegalArgumentException.class);
  assertThat(new com.nigson.integration.bootstrap.internal.FoundationConfiguration("stage",true)).isNotNull();
 }
 @Test void retryIsClosedTypedAndSafe(){
  assertThat(JobValues.retry(EventCodec.JSON.readTree("{\"reason\":\"Operator requested inspection\"}")).reason()).isEqualTo("Operator requested inspection");
  for(String raw:new String[]{"{}","{\"reason\":1}","{\"reason\":\" \"}","{\"reason\":\"ok\",\"providerKey\":\"x\"}","{\"reason\":\"bad\\nreason\"}"})assertThatThrownBy(()->JobValues.retry(EventCodec.JSON.readTree(raw))).isInstanceOf(RuntimeException.class);
  assertThat(JobValues.retry(EventCodec.JSON.readTree("{\"reason\":\"private justification\"}")).toString()).doesNotContain("justification");
 }
 @Test void boundedQueriesRejectUnknownStatesSortInjectionAndNumericCoercion(){
  assertThat(JobValues.kind("PAYMENT_RECONCILE")).isEqualTo(JobValues.Kind.PAYMENT_RECONCILE);
  assertThatThrownBy(()->JobValues.state("PAID")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->new JobValues.Query(null,null,0,101,"id,asc")).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->new JobValues.Query(null,null,0,20,"id;DELETE")).isInstanceOf(RuntimeException.class);
  for(String text:new String[]{"-1","01","1e2","2147483648"," 1"})assertThatThrownBy(()->JobValues.number(text,0)).isInstanceOf(RuntimeException.class);
 }
}
