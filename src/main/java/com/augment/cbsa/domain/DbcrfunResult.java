package com.augment.cbsa.domain;

import java.util.Objects;
import java.util.Set;

public record DbcrfunResult(
        boolean paymentSuccess,
        String failCode,
        AccountDetails account,
        String message
) {

    private static final Set<String> NOT_FOUND_FAIL_CODES = Set.of("1");
    private static final Set<String> CONFLICT_FAIL_CODES = Set.of("3", "4");

    public DbcrfunResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (paymentSuccess && account == null) {
            throw new IllegalArgumentException("Successful results must include an account");
        }
        if (!paymentSuccess && account != null) {
            throw new IllegalArgumentException("Failure results must not include an account");
        }
        if (!paymentSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static DbcrfunResult success(AccountDetails account) {
        Objects.requireNonNull(account, "account must not be null");
        return new DbcrfunResult(true, "0", account, null);
    }

    public static DbcrfunResult failure(String failCode, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new DbcrfunResult(false, failCode, null, message);
    }

    public boolean isNotFoundFailure() {
        return !paymentSuccess && NOT_FOUND_FAIL_CODES.contains(failCode);
    }

    public boolean isConflictFailure() {
        return !paymentSuccess && CONFLICT_FAIL_CODES.contains(failCode);
    }

    public boolean isInsufficientFundsFailure() {
        return !paymentSuccess && "3".equals(failCode);
    }

    public boolean isDisallowedAccountTypeFailure() {
        return !paymentSuccess && "4".equals(failCode);
    }
}