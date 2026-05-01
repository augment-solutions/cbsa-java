package com.augment.cbsa.domain;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InqacccuResultTest {

    @Test
    void successRejectsNullAccountsWithExplicitMessage() {
        assertThatThrownBy(() -> InqacccuResult.success(1L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("accounts must not be null");
    }

    @Test
    void failureRejectsNullMessageWithExplicitMessage() {
        assertThatThrownBy(() -> InqacccuResult.failure("1", 1L, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must include a non-blank message");
    }

    @Test
    void failureRejectsEmbeddedAccounts() {
        assertThatThrownBy(() -> new InqacccuResult(false, "3", 1L, false, List.of(account()), "boom"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must not include account data");
    }

    private AccountDetails account() {
        return new AccountDetails(
                "987654",
                1L,
                2L,
                "ISA",
                new java.math.BigDecimal("1.50"),
                java.time.LocalDate.of(2024, 1, 2),
                new java.math.BigDecimal("250.00"),
                java.time.LocalDate.of(2024, 2, 3),
                java.time.LocalDate.of(2024, 3, 4),
                new java.math.BigDecimal("1500.25"),
                new java.math.BigDecimal("1499.75")
        );
    }
}