package com.augment.cbsa.error;

import java.util.Objects;

public abstract class CbsaBusinessException extends RuntimeException {

    private final String errorCode;

    protected CbsaBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    protected CbsaBusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public String getErrorCode() {
        return errorCode;
    }
}
