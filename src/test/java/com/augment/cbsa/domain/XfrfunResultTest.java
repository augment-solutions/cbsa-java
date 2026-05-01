package com.augment.cbsa.domain;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XfrfunResultTest {

    @Test
    void successFactoryStoresNullMessage() {
        XfrfunResult result = XfrfunResult.success(
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("200.00")
        );

        assertThat(result.transferSuccess()).isTrue();
        assertThat(result.message()).isNull();
    }

    @Test
    void successConstructorRejectsBlankMessage() {
        assertThatThrownBy(() -> new XfrfunResult(
                true,
                "0",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("200.00"),
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Successful results must not include a message");
    }

    @Test
    void successConstructorRejectsNonBlankMessage() {
        assertThatThrownBy(() -> new XfrfunResult(
                true,
                "0",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("200.00"),
                "leaked"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Successful results must not include a message");
    }

    @Test
    void failureRejectsNullMessage() {
        assertThatThrownBy(() -> XfrfunResult.failure("1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message must not be null");
    }
}
