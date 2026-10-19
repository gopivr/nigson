package com.nigson.commerce.reporting;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.nigson.shared.problem.ApiValues;

/** API-075 closed inputs and sanitized output. Periods use UTC instants and [from,to). */
public final class ReportValues {
    private ReportValues() {}
    public record Query(Instant from, Instant to, String currency, UUID organizationId) {
        public Query {
            if (from == null || to == null || currency == null || !currency.matches("[A-Z]{3}") ||
                    !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0)
                throw ApiValues.invalid();
            if (organizationId != null) ApiValues.uuid(organizationId.toString());
        }
    }
    public record Money(String amount, String currency) {}
    public record Product(UUID skuId, String quantity, Money revenue) {}
    public record Stock(UUID skuId, UUID locationId, String onHand, String reserved,
                        String allocated, String available, long version) {}
    public record Period(Instant from, Instant to) {}
    public record Summary(Period period, String currency, Money sales, long orders,
                          long customers, List<Product> products, List<Stock> stock) {
        public Summary { products = List.copyOf(products); stock = List.copyOf(stock); }
    }
}
