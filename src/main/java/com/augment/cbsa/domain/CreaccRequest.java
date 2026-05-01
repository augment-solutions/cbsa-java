package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record CreaccRequest(
        long customerNumber,
        String accountType,
        BigDecimal interestRate,
        long overdraftLimit,
        BigDecimal availableBalance,
        BigDecimal actualBalance
) {

    public CreaccRequest {
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(interestRate, "interestRate must not be null");
        Objects.requireNonNull(availableBalance, "availableBalance must not be null");
        Objects.requireNonNull(actualBalance, "actualBalance must not be null");

        if (customerNumber < 0 || customerNumber > 9_999_999_999L) {
            throw new IllegalArgumentException("customerNumber must be between 0 and 9999999999");
        }
        if (accountType.length() > 8) {
            throw new IllegalArgumentException("accountType must be at most 8 characters");
        }
        if (overdraftLimit < 0 || overdraftLimit > 99_999_999L) {
            throw new IllegalArgumentException("overdraftLimit must be between 0 and 99999999");
        }

        interestRate = scale(interestRate);
        availableBalance = scale(availableBalance);
        actualBalance = scale(actualBalance);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}