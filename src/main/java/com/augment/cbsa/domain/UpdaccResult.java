package com.augment.cbsa.domain;

import java.util.Objects;
import java.util.Set;

public record UpdaccResult(
        boolean updateSuccess,
        String failCode,
        AccountDetails account,
        String message
) {

    private static final Set<String> NOT_FOUND_FAIL_CODES = Set.of("1");
    private static final Set<String> VALIDATION_FAIL_CODES = Set.of("2");

    public UpdaccResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (updateSuccess && account == null) {
            throw new IllegalArgumentException("Successful results must include an account");
        }
        if (!updateSuccess && account != null) {
            throw new IllegalArgumentException("Failure results must not include an account");
        }
        if (!updateSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static UpdaccResult success(AccountDetails account) {
        Objects.requireNonNull(account, "account must not be null");
        return new UpdaccResult(true, " ", account, null);
    }

    public static UpdaccResult failure(String failCode, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new UpdaccResult(false, failCode, null, message);
    }

    public boolean isNotFoundFailure() {
        return !updateSuccess && NOT_FOUND_FAIL_CODES.contains(failCode);
    }

    public boolean isValidationFailure() {
        return !updateSuccess && VALIDATION_FAIL_CODES.contains(failCode);
    }
}