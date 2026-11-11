package com.nigson.integration.providers;

import java.math.BigDecimal;

/** Address lookup is a provider read; postal addresses and credentials never enter logs. */
@FunctionalInterface
public interface GeocodingProvider {
 record Address(String line1,String line2,String city,String region,String postalCode,String countryCode) {
  @Override public String toString(){return "GeocodingAddress[protected]";}
 }
 record Point(BigDecimal latitude,BigDecimal longitude) {}
 Point resolve(Address address);
}
