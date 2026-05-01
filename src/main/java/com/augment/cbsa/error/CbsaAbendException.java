package com.augment.cbsa.error;

import java.util.Objects;

public class CbsaAbendException extends RuntimeException {

    private final String abendCode;

    public CbsaAbendException(String abendCode, String message) {
        super(message);
        this.abendCode = Objects.requireNonNull(abendCode, "abendCode must not be null");
    }

    public CbsaAbendException(String abendCode, String message, Throwable cause) {
        super(message, cause);
        this.abendCode = Objects.requireNonNull(abendCode, "abendCode must not be null");
    }

    public String getAbendCode() {
        return abendCode;
    }
}