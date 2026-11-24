package com.nigson.acceptance.catalogue;
import com.nigson.commerce.inventory.StockAvailability;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StockAvailabilityTest {
 @Test void classifiesThresholdBoundaryZeroAndUnbuyablePacks(){assertThat(stock("0","1").status()).isEqualTo("OUT_OF_STOCK");assertThat(stock("-1","1").availableQuantity()).isEqualTo("0");assertThat(stock("1","2").status()).isEqualTo("OUT_OF_STOCK");assertThat(stock("10","1").status()).isEqualTo("LOW_STOCK");assertThat(stock("10.001","0.001").status()).isEqualTo("IN_STOCK");assertThat(stock("0.5","0.1").status()).isEqualTo("LOW_STOCK");}
 private StockAvailability.Stock stock(String qty,String pack){return StockAvailability.classify(new BigDecimal(qty),new BigDecimal(pack),BigDecimal.TEN);}
}
