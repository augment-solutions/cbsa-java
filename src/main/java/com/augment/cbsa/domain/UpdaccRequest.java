package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record UpdaccRequest(
        long accountNumber,
        String accountType,
        BigDecimal interestRate,
        long overdraftLimit
) {

    public UpdaccRequest {
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(interestRate, "interestRate must not be null");
    }
}