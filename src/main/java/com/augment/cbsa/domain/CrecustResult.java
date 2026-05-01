package com.augment.cbsa.domain;

import java.util.Objects;
import java.util.Set;

public record CrecustResult(
        boolean creationSuccess,
        String failCode,
        CustomerDetails customer,
        String message
) {

    private static final Set<String> VALIDATION_FAIL_CODES = Set.of("T", "O", "Y", "Z");
    private static final Set<String> CREDIT_FAIL_CODES = Set.of("A", "B", "C", "D", "E", "F", "G", "H");

    public CrecustResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (creationSuccess && customer == null) {
            throw new IllegalArgumentException("Successful results must include a customer");
        }
        if (!creationSuccess && customer != null) {
            throw new IllegalArgumentException("Failure results must not include a customer");
        }
        if (!creationSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static CrecustResult success(CustomerDetails customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        return new CrecustResult(true, " ", customer, null);
    }

    public static CrecustResult failure(String failCode, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new CrecustResult(false, failCode, null, message);
    }

    public boolean isValidationFailure() {
        return !creationSuccess && VALIDATION_FAIL_CODES.contains(failCode);
    }

    public boolean isCreditFailure() {
        return !creationSuccess && CREDIT_FAIL_CODES.contains(failCode);
    }
}
