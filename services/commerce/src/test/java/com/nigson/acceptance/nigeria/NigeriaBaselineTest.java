package com.nigson.acceptance.nigeria;
import com.nigson.commerce.market.*;
import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
class NigeriaBaselineTest {
 @Test void approvedBaselineContainsNoInventedSellingPolicies(){String url=System.getenv("NIGSON_TEST_JDBC_URL");Assumptions.assumeTrue(url!=null,"nigeria-check supplies isolated PostgreSQL");if(!url.matches("jdbc:postgresql://127.0.0.1:[0-9]+/nigson"))throw new IllegalArgumentException("LOCAL_TEST_DB_REQUIRED");var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"nigson_commerce_app","mock-commerce-runtime"));assertThat(jdbc.queryForObject("SELECT minor_unit::text||':'||active::text FROM commerce.currency WHERE code='NGN'",String.class)).isEqualTo("2:true");var id=jdbc.queryForObject("SELECT id FROM commerce.market WHERE code='NG'",java.util.UUID.class);var v=new JdbcMarketRepository(jdbc,Clock.systemUTC()).find(id,false);assertThat(v.name()).isEqualTo("Nigeria");assertThat(v.currency()).isEqualTo("NGN");assertThat(v.enabled()).isFalse();assertThat(v.policyRevision()).isEqualTo(1);assertThat(v.version()).isZero();assertThat(v.taxPolicy().isEmpty()).isTrue();assertThat(v.retailPriceBookId()).isNull();assertThat(v.returnsPolicyRevision()).isNull();assertThat(v.destinations()).hasSize(1);assertThat(v.destinations().getFirst().countryCode()).isEqualTo("NG");assertThat(v.destinations().getFirst().deliveryEnabled()).isFalse();assertThat(v.destinations().getFirst().addressRules().isEmpty()).isTrue();assertThat(new JdbcMarketRepository(jdbc,Clock.systemUTC()).publicMarkets()).isEmpty();assertThatThrownBy(()->MarketValues.tax(v.taxPolicy())).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->MarketValues.address(v.destinations().getFirst().addressRules())).hasMessage("VALIDATION_ERROR");}
}
