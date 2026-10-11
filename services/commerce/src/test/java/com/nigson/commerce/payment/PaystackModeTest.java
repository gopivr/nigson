package com.nigson.commerce.payment;

import com.nigson.commerce.payment.internal.infrastructure.external.paystack.PaystackAdapter;
import com.nigson.shared.security.ProviderHttp;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Constructor-only genuine-target checks never make HTTP requests or qualify an account. */
class PaystackModeTest {
 @Test void syntheticKeysCannotReachGenuineTargets(){
  for(var mode:new ProviderHttp.Mode[]{ProviderHttp.Mode.TEST,ProviderHttp.Mode.LIVE})
   for(String suffix:new String[]{"configuration_validation_only","synthetic","fixture","shape_only","changeme","your_secret","test_key","0000000000000000000000000000000000000000"})
    assertThatThrownBy(()->new PaystackAdapter("stage",mode,"https://api.paystack.co","sk_"+mode.name().toLowerCase(java.util.Locale.ROOT)+"_"+suffix)).isInstanceOf(IllegalArgumentException.class).hasMessage("PAYSTACK_SYNTHETIC_KEY_DENIED");
 }
 @Test void targetsAndProductionFailClosedBeforeHttp(){
  String key="sk_test_abcdef0123456789abcdef0123456789abcdef01"; // shape-only constructor fixture; never sent
  for(String endpoint:new String[]{"http://api.paystack.co","https://api.paystack.co.evil.invalid","https://127.0.0.1","https://api.paystack.co:443","https://operator@api.paystack.co","https://api.paystack.co?redirect=1","https://api.paystack.co/transaction"})
   assertThatThrownBy(()->new PaystackAdapter("stage",ProviderHttp.Mode.TEST,endpoint,key)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new PaystackAdapter("prod",ProviderHttp.Mode.TEST,"https://api.paystack.co",key)).hasMessage("PRODUCTION_MODE_INVALID");
  assertThatThrownBy(()->new PaystackAdapter("stage",ProviderHttp.Mode.MOCK,"http://paystack-mock:8080","sk_mock_nigson")).hasMessage("MOCK_TARGET_INVALID");
  assertThatThrownBy(()->new PaystackAdapter("prod",ProviderHttp.Mode.MOCK,"http://paystack-mock:8080","sk_mock_nigson")).hasMessage("MOCK_TARGET_INVALID");
 }
 @Test void modeSwitchUsesSameAdapterWithoutNetworkOrFallback(){
  assertThat(new PaystackAdapter("test",ProviderHttp.Mode.MOCK,"http://127.0.0.1:1","sk_mock_nigson")).isNotNull();
  // Only syntactic mode/endpoint/key validation is exercised here. No genuine qualification implied.
  assertThat(new PaystackAdapter("stage",ProviderHttp.Mode.TEST,"https://api.paystack.co","sk_test_abcdef0123456789abcdef0123456789abcdef01")).isNotNull();
  assertThat(new PaystackAdapter("prod",ProviderHttp.Mode.LIVE,"https://api.paystack.co","sk_live_abcdef0123456789abcdef0123456789abcdef01")).isNotNull();
  assertThatThrownBy(()->new PaystackAdapter("stage",ProviderHttp.Mode.TEST,"https://api.paystack.co","sk_live_abcdef0123456789abcdef0123456789abcdef01")).hasMessage("PAYSTACK_KEY_MODE_INVALID");
 }
 @Test void koboConversionNeverRoundsOrAcceptsThirdFractionalZeros(){
  assertThat(PaystackAdapter.kobo("1234.56")).isEqualTo(123456);
  assertThat(PaystackAdapter.kobo("0.01")).isEqualTo(1);
  for(String input:new String[]{"1.000","1.001","0","0.00","-1","1e2","1,000","NaN"," 1.00","1000000000000000.00"})
   assertThatThrownBy(()->PaystackAdapter.kobo(input)).isInstanceOf(IllegalArgumentException.class);
 }
}
