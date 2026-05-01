package com.augment.cbsa.domain;

import java.util.Objects;

public record DelcusResult(
        boolean deleteSuccess,
        String failCode,
        long customerNumber,
        CustomerDetails customer,
        String message
) {

    public DelcusResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (deleteSuccess && customer == null) {
            throw new IllegalArgumentException("Successful results must include a customer");
        }
        if (!deleteSuccess && customer != null) {
            throw new IllegalArgumentException("Failure results must not include a customer");
        }
        if (!deleteSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static DelcusResult success(CustomerDetails customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        return new DelcusResult(true, " ", customer.customerNumber(), customer, null);
    }

    public static DelcusResult failure(String failCode, long customerNumber, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new DelcusResult(false, failCode, customerNumber, null, message);
    }

    public boolean isNotFoundFailure() {
        return !deleteSuccess && "1".equals(failCode);
    }

    public boolean isRandomRetryExhaustedFailure() {
        return !deleteSuccess && "R".equals(failCode);
    }
}
