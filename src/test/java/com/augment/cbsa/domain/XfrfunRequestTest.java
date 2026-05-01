package com.augment.cbsa.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XfrfunRequestTest {

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new XfrfunRequest(12345678L, 87654321L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount must not be null");
    }

    @Test
    void scalesAmountToTwoDecimalPlaces() {
        XfrfunRequest request = new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.123456"));

        assertThat(request.amount()).isEqualByComparingTo("25.12");
        assertThat(request.amount().scale()).isEqualTo(2);
    }

    @Test
    void preservesAccountNumbers() {
        XfrfunRequest request = new XfrfunRequest(11111111L, 22222222L, new BigDecimal("100.00"));

        assertThat(request.fromAccountNumber()).isEqualTo(11111111L);
        assertThat(request.toAccountNumber()).isEqualTo(22222222L);
    }
}
