package com.augment.cbsa.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record XfrfunResult(
        boolean transferSuccess,
        String failCode,
        BigDecimal fromAvailableBalance,
        BigDecimal fromActualBalance,
        BigDecimal toAvailableBalance,
        BigDecimal toActualBalance,
        String message
) {

    public XfrfunResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (transferSuccess) {
            Objects.requireNonNull(fromAvailableBalance, "Successful results must include the from available balance");
            Objects.requireNonNull(fromActualBalance, "Successful results must include the from actual balance");
            Objects.requireNonNull(toAvailableBalance, "Successful results must include the to available balance");
            Objects.requireNonNull(toActualBalance, "Successful results must include the to actual balance");
            if (message != null && !message.isBlank()) {
                throw new IllegalArgumentException("Successful results must not include a message");
            }
        } else {
            if (fromAvailableBalance != null || fromActualBalance != null || toAvailableBalance != null || toActualBalance != null) {
                throw new IllegalArgumentException("Failure results must not include balances");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Failure results must include a non-blank message");
            }
        }
    }

    public static XfrfunResult success(
            BigDecimal fromAvailableBalance,
            BigDecimal fromActualBalance,
            BigDecimal toAvailableBalance,
            BigDecimal toActualBalance
    ) {
        return new XfrfunResult(true, "0", fromAvailableBalance, fromActualBalance, toAvailableBalance, toActualBalance, null);
    }

    public static XfrfunResult failure(String failCode, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new XfrfunResult(false, failCode, null, null, null, null, message);
    }

    public boolean isFromAccountNotFoundFailure() {
        return !transferSuccess && "1".equals(failCode);
    }

    public boolean isToAccountNotFoundFailure() {
        return !transferSuccess && "2".equals(failCode);
    }

    public boolean isInvalidAmountFailure() {
        return !transferSuccess && "4".equals(failCode);
    }
}