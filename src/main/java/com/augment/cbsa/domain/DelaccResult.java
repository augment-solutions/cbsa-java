package com.augment.cbsa.domain;

import java.util.Objects;

public record DelaccResult(
        boolean deleteSuccess,
        String failCode,
        long accountNumber,
        AccountDetails account,
        String message
) {

    public DelaccResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (deleteSuccess && account == null) {
            throw new IllegalArgumentException("Successful results must include an account");
        }
        if (!deleteSuccess && account != null) {
            throw new IllegalArgumentException("Failure results must not include an account");
        }
        if (!deleteSuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static DelaccResult success(AccountDetails account) {
        Objects.requireNonNull(account, "account must not be null");
        return new DelaccResult(true, " ", account.accountNumber(), account, null);
    }

    public static DelaccResult failure(String failCode, long accountNumber, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new DelaccResult(false, failCode, accountNumber, null, message);
    }

    public boolean isNotFoundFailure() {
        return !deleteSuccess && "1".equals(failCode);
    }
}
