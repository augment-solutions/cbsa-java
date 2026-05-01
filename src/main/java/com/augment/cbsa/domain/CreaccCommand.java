package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public record CreaccCommand(
        String sortcode,
        long customerNumber,
        String accountType,
        BigDecimal interestRate,
        BigDecimal overdraftLimit,
        BigDecimal availableBalance,
        BigDecimal actualBalance,
        LocalDate opened,
        LocalDate lastStatementDate,
        LocalDate nextStatementDate,
        long transactionReference,
        LocalDate transactionDate,
        LocalTime transactionTime
) {

    public CreaccCommand {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(interestRate, "interestRate must not be null");
        Objects.requireNonNull(overdraftLimit, "overdraftLimit must not be null");
        Objects.requireNonNull(availableBalance, "availableBalance must not be null");
        Objects.requireNonNull(actualBalance, "actualBalance must not be null");
        Objects.requireNonNull(opened, "opened must not be null");
        Objects.requireNonNull(lastStatementDate, "lastStatementDate must not be null");
        Objects.requireNonNull(nextStatementDate, "nextStatementDate must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");

        if (sortcode.length() != 6) {
            throw new IllegalArgumentException("sortcode must be exactly 6 characters");
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