package com.augment.cbsa.error;

import java.util.Objects;

public record CbsaFailureResponse(String failCode, String message) {

    public CbsaFailureResponse {
        Objects.requireNonNull(failCode, "failCode must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}