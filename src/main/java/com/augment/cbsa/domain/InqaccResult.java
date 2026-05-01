package com.augment.cbsa.domain;

import java.util.Objects;

public record InqaccResult(
        boolean inquirySuccess,
        String failCode,
        long accountNumber,
        AccountDetails account,
        String message
) {

    public InqaccResult {
        Objects.requireNonNull(failCode, "failCode must not be null");

        if (inquirySuccess && account == null) {
            throw new IllegalArgumentException("Successful results must include an account");
        }

        if (!inquirySuccess && account != null) {
            throw new IllegalArgumentException("Failure results must not include an account");
        }

        if (!inquirySuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static InqaccResult success(AccountDetails account) {
        Objects.requireNonNull(account, "account must not be null");
        return new InqaccResult(true, "0", account.accountNumber(), account, null);
    }

    public static InqaccResult failure(String failCode, long accountNumber, String message) {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new InqaccResult(false, failCode, accountNumber, null, message);
    }

    public boolean isNotFoundFailure() {
        return !inquirySuccess && "1".equals(failCode);
    }
}