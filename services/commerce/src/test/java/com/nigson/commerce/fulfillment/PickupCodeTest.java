package com.nigson.commerce.fulfillment;

import com.nigson.shared.problem.Problem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PickupCodeTest {
    @Test void derivesABoundedOperatorSearchableCodeFromTheOrderNumber() {
        String orderNumber = "NIG_0123456789ABCDEF0123456789ABCDEF";
        String code = JdbcShipmentRepository.pickupCode(orderNumber);
        assertThat(code).isEqualTo("6789ABCDEF");
        assertThat(code).matches("[A-Z0-9]{10}");
        assertThat(orderNumber).endsWith(code);
        assertThat(JdbcShipmentRepository.pickupCode("NIG_0123456789abcdef0123456789abcdef")).isEqualTo(code);
    }

    @Test void rejectsAReferenceTooShortToProduceAPickupCode() {
        assertThatThrownBy(() -> JdbcShipmentRepository.pickupCode("NIG_123"))
            .isInstanceOfSatisfying(Problem.Failure.class,
                failure -> assertThat(failure.getMessage()).isEqualTo("FULFILLMENT_RULE_VIOLATION"));
    }
}
