package com.nigson.experience.engagement;

import com.nigson.shared.events.EventCodec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EngagementValuesTest {
 @Test void productReviewRequiresExactPurchaseAssociationAndRating(){
  var good=EventCodec.JSON.readTree("{\"kind\":\"PRODUCT\",\"productId\":\"11111111-1111-4111-8111-111111111111\",\"orderLineId\":\"22222222-2222-4222-8222-222222222222\",\"rating\":5,\"body\":\"Good product\"}");
  assertThat(EngagementValues.review(good).rating()).isEqualTo(5);
  for(String bad:new String[]{"{\"kind\":\"PRODUCT\",\"body\":\"Good\",\"verifiedPurchase\":true}","{\"kind\":\"PRODUCT\",\"productId\":\"11111111-1111-4111-8111-111111111111\",\"orderLineId\":\"22222222-2222-4222-8222-222222222222\",\"body\":\"Good\"}","{\"kind\":\"TESTIMONIAL\",\"productId\":\"11111111-1111-4111-8111-111111111111\",\"body\":\"Good\"}"})assertThatThrownBy(()->EngagementValues.review(EventCodec.JSON.readTree(bad))).isInstanceOf(RuntimeException.class);
 }
 @Test void campaignsAcceptOnlyClosedDisplayFieldsAndSafeLocalUrls(){
  var good=EventCodec.JSON.readTree("{\"code\":\"WELCOME_1\",\"title\":\"Welcome\",\"marketId\":\"33333333-3333-4333-8333-333333333333\",\"displayConfig\":{\"headline\":\"Hello\",\"ctaUrl\":\"/offers\"},\"startsAt\":\"2026-10-01T00:00:00Z\",\"endsAt\":\"2026-11-01T00:00:00Z\",\"published\":false}");
  assertThat(EngagementValues.campaign(good).displayConfig().path("headline").asString()).isEqualTo("Hello");
  for(String bad:new String[]{"{\"code\":\"WELCOME_1\",\"title\":\"Welcome\",\"marketId\":\"33333333-3333-4333-8333-333333333333\",\"displayConfig\":{\"discountPercent\":\"99\"},\"startsAt\":\"2026-10-01T00:00:00Z\",\"endsAt\":\"2026-11-01T00:00:00Z\",\"published\":true}","{\"code\":\"WELCOME_1\",\"title\":\"Welcome\",\"marketId\":\"33333333-3333-4333-8333-333333333333\",\"displayConfig\":{\"ctaUrl\":\"https://attacker.invalid\"},\"startsAt\":\"2026-10-01T00:00:00Z\",\"endsAt\":\"2026-11-01T00:00:00Z\",\"published\":true}"})assertThatThrownBy(()->EngagementValues.campaign(EventCodec.JSON.readTree(bad))).isInstanceOf(RuntimeException.class);
 }
}
