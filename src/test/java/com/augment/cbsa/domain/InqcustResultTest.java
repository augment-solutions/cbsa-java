package com.augment.cbsa.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InqcustResultTest {

    @Test
    void successRejectsNullCustomerWithExplicitMessage() {
        assertThatThrownBy(() -> InqcustResult.success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("customer must not be null");
    }
}
