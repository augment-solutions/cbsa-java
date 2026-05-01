package com.augment.cbsa.domain;

import java.util.Objects;
import java.util.Set;

public record UpdcustResult(
        boolean updateSuccess,
        String failCode,
        CustomerDetails customer,
        String message
) {

    private static final Set<String> NOT_FOUND_FAIL_CODES = Set.of("1");
    private static final Set<String> VALIDATION_FAIL_CODES = Set.of("4", "T");

    public UpdcustResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (updateSuccess && customer == null) {
            throw new IllegalArgumentException("Successful results must include a customer");
        }
        if (!updateSuccess && customer != null) {
            throw new IllegalArgumentException("Failure results must not include a customer");
        }
        if (!updateSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static UpdcustResult success(CustomerDetails customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        return new UpdcustResult(true, " ", customer, null);
    }

    public static UpdcustResult failure(String failCode, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new UpdcustResult(false, failCode, null, message);
    }

    public boolean isNotFoundFailure() {
        return !updateSuccess && NOT_FOUND_FAIL_CODES.contains(failCode);
    }

    public boolean isValidationFailure() {
        return !updateSuccess && VALIDATION_FAIL_CODES.contains(failCode);
    }
}
