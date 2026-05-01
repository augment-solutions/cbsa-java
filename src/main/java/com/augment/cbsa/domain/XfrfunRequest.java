package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record XfrfunRequest(
        long fromAccountNumber,
        long toAccountNumber,
        BigDecimal amount
) {

    public XfrfunRequest {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
    }
}