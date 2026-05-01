package com.augment.cbsa.domain;

import java.util.Objects;
import java.util.Set;

public record CreaccResult(
        boolean creationSuccess,
        String failCode,
        AccountDetails account,
        String message
) {

    private static final Set<String> VALIDATION_FAIL_CODES = Set.of("A");
    private static final Set<String> NOT_FOUND_FAIL_CODES = Set.of("1");
    private static final Set<String> CAPACITY_FAIL_CODES = Set.of("8");

    public CreaccResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (creationSuccess && account == null) {
            throw new IllegalArgumentException("Successful results must include an account");
        }
        if (!creationSuccess && account != null) {
            throw new IllegalArgumentException("Failure results must not include an account");
        }
        if (!creationSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static CreaccResult success(AccountDetails account) {
        Objects.requireNonNull(account, "account must not be null");
        return new CreaccResult(true, " ", account, null);
    }

    public static CreaccResult failure(String failCode, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new CreaccResult(false, failCode, null, message);
    }

    public boolean isValidationFailure() {
        return !creationSuccess && VALIDATION_FAIL_CODES.contains(failCode);
    }

    public boolean isNotFoundFailure() {
        return !creationSuccess && NOT_FOUND_FAIL_CODES.contains(failCode);
    }

    public boolean isCapacityFailure() {
        return !creationSuccess && CAPACITY_FAIL_CODES.contains(failCode);
    }
}