package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

public record AccountDetails(
        String sortcode,
        long customerNumber,
        long accountNumber,
        String accountType,
        BigDecimal interestRate,
        LocalDate opened,
        BigDecimal overdraftLimit,
        LocalDate lastStatementDate,
        LocalDate nextStatementDate,
        BigDecimal availableBalance,
        BigDecimal actualBalance
) {

    private static final Pattern SORTCODE_PATTERN = Pattern.compile("[0-9]{6}");

    public AccountDetails {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(interestRate, "interestRate must not be null");
        Objects.requireNonNull(opened, "opened must not be null");
        Objects.requireNonNull(overdraftLimit, "overdraftLimit must not be null");
        Objects.requireNonNull(availableBalance, "availableBalance must not be null");
        Objects.requireNonNull(actualBalance, "actualBalance must not be null");

        if (!SORTCODE_PATTERN.matcher(sortcode).matches()) {
            throw new IllegalArgumentException("sortcode must be exactly 6 ASCII digits");
        }

        if (accountType.length() > 8) {
            throw new IllegalArgumentException("accountType must be at most 8 characters");
        }

        interestRate = scale(interestRate);
        overdraftLimit = scale(overdraftLimit);
        availableBalance = scale(availableBalance);
        actualBalance = scale(actualBalance);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}