package com.nigson.commerce.payment;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.nigson.commerce.order.OrderPolicy;
import static org.assertj.core.api.Assertions.*;

/** Hosted checkout must return the browser to our own storefront route. */
class CheckoutPaymentReturnUrlTest {
 private static final List<UUID> LOCATIONS=List.of(UUID.randomUUID());
 private static OrderPolicy policy(boolean enabled,String mode,String environment){return new OrderPolicy(enabled,environment,mode,"synthetic-merchant",LOCATIONS,900);}

 @Test void disabledCheckoutDoesNotRequireAReturnTarget(){assertThat(policy(false,"TEST","local").checkoutReturnUrl(null)).isNull();}

 @Test void localTestAcceptsHttpsOrLoopbackHttp(){assertThat(policy(true,"TEST","local").checkoutReturnUrl("https://shop.nigson.com/checkout/success")).isEqualTo("https://shop.nigson.com/checkout/success");assertThat(policy(true,"TEST","local").checkoutReturnUrl("http://localhost:4200/checkout/success")).isEqualTo("http://localhost:4200/checkout/success");assertThat(policy(true,"TEST","test").checkoutReturnUrl("http://127.0.0.1:4200/checkout/success")).isEqualTo("http://127.0.0.1:4200/checkout/success");}

 @Test void enabledHostedCheckoutWithoutAReturnTargetFailsClosed(){assertThatThrownBy(()->policy(true,"TEST","local").checkoutReturnUrl(null)).hasMessage("CHECKOUT_RETURN_URL_REQUIRED");assertThatThrownBy(()->policy(true,"TEST","local").checkoutReturnUrl("  ")).hasMessage("CHECKOUT_RETURN_URL_REQUIRED");}

 @Test void onlyMockCheckoutMayOmitAReturnTarget(){assertThat(policy(true,"MOCK","test").checkoutReturnUrl(null)).isNull();}

 @Test void remoteHttpAndAmbiguousTargetsAreRejected(){for(String unsafe:List.of("http://shop.example.com/checkout/success","javascript:alert(1)","https://shop.example.com/checkout/success?next=//evil.test","https://shop.example.com/checkout/success#fragment","https://user:secret@shop.example.com/checkout/success","/checkout/success","//shop.example.com/checkout/success"))
  assertThatThrownBy(()->policy(true,"TEST","local").checkoutReturnUrl(unsafe)).hasMessage("CHECKOUT_RETURN_URL_REQUIRED");}

 @Test void liveRequiresHttps(){assertThatThrownBy(()->policy(true,"LIVE","prod").checkoutReturnUrl("http://shop.nigson.com/checkout/success")).hasMessage("CHECKOUT_RETURN_URL_REQUIRED");assertThat(policy(true,"LIVE","prod").checkoutReturnUrl("https://shop.nigson.com/checkout/success")).isEqualTo("https://shop.nigson.com/checkout/success");}
}
