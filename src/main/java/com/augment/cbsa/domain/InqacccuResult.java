package com.augment.cbsa.domain;

import java.util.List;
import java.util.Objects;

public record InqacccuResult(
        boolean inquirySuccess,
        String failCode,
        long customerNumber,
        boolean customerFound,
        List<AccountDetails> accounts,
        String message
) {

    public InqacccuResult {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(accounts, "accounts must not be null");
        accounts = List.copyOf(accounts);

        if (inquirySuccess && !customerFound) {
            throw new IllegalArgumentException("Successful results must mark the customer as found");
        }

        if (!inquirySuccess && !accounts.isEmpty()) {
            throw new IllegalArgumentException("Failure results must not include account data");
        }

        if (!inquirySuccess && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("Failure results must include a non-blank message");
        }
    }

    public static InqacccuResult success(long customerNumber, List<AccountDetails> accounts) {
        return new InqacccuResult(true, "0", customerNumber, true, accounts, null);
    }

    public static InqacccuResult failure(String failCode, long customerNumber, boolean customerFound, String message) {
        return new InqacccuResult(false, failCode, customerNumber, customerFound, List.of(), message);
    }

    public boolean isNotFoundFailure() {
        return !inquirySuccess && "1".equals(failCode);
    }

    public boolean isRandomRetryExhaustedFailure() {
        return !inquirySuccess && "R".equals(failCode);
    }
}