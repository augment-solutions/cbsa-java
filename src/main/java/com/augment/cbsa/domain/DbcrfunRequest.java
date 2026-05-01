package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record DbcrfunRequest(
        long accountNumber,
        BigDecimal amount,
        DbcrfunOrigin origin
) {

    public DbcrfunRequest {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
    }
}