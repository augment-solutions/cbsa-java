package com.augment.cbsa.error;

public class CbsaAbendException extends CbsaBusinessException {

    public CbsaAbendException(String abendCode, String message) {
        super(abendCode, message);
    }

    public CbsaAbendException(String abendCode, String message, Throwable cause) {
        super(abendCode, message, cause);
    }

    public String getAbendCode() {
        return getErrorCode();
    }
}
