package com.augment.cbsa.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InqaccResultTest {

    @Test
    void successRejectsNullAccountWithExplicitMessage() {
        assertThatThrownBy(() -> InqaccResult.success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account must not be null");
    }

    @Test
    void failureRejectsNullMessageWithExplicitMessage() {
        assertThatThrownBy(() -> InqaccResult.failure("1", 1L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message must not be null");
    }
}