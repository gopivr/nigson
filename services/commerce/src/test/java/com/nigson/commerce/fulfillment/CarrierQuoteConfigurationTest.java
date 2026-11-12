package com.nigson.commerce.fulfillment;
import com.nigson.commerce.market.MarketEligibility;
import com.nigson.commerce.platform.*;
import com.nigson.commerce.pricing.*;
import com.nigson.commerce.security.ActorDelegation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CarrierQuoteConfigurationTest {
 @Test void selectedCarrierUsesItsRegisteredOutletWithoutLegacyPickupConfiguration(){
  var env=new MockEnvironment().withProperty("NIGSON_CARRIER_PROVIDER","PANDAGO").withProperty("NIGSON_FULFILLMENT_MODE","SANDBOX");
  assertThat(new CarrierQuoteConfiguration().deliveryQuoteService(mock(JdbcTemplate.class),mock(PlatformTransactionManager.class),mock(PricingAuthority.class),mock(PricingProviders.class),mock(MarketEligibility.class),mock(FulfillmentPolicy.class),mock(CarrierRates.class),mock(ActorDelegation.class),mock(PlatformStore.class),mock(AuditWriter.class),mock(Pricing.class),mock(FrozenPrices.class),env)).isNotNull();
 }
}
