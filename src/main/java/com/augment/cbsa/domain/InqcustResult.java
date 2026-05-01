package com.augment.cbsa.domain;

import java.util.Objects;

public record InqcustResult(
        boolean inquirySuccess,
        String failCode,
        long customerNumber,
        CustomerDetails customer,
        String message
) {

    public InqcustResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (inquirySuccess && customer == null) {
            throw new IllegalArgumentException("Successful results must include a customer");
        }

        if (!inquirySuccess && customer != null) {
            throw new IllegalArgumentException("Failure results must not include a customer");
        }

        if (!inquirySuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static InqcustResult success(CustomerDetails customer) {
        return new InqcustResult(true, "0", customer.customerNumber(), customer, null);
    }

    public static InqcustResult failure(String failCode, long customerNumber, String message) {
        return new InqcustResult(false, failCode, customerNumber, null, message);
    }

    public boolean isNotFoundFailure() {
        return !inquirySuccess && "1".equals(failCode);
    }

    public boolean isRandomRetryExhaustedFailure() {
        return !inquirySuccess && "R".equals(failCode);
    }
}