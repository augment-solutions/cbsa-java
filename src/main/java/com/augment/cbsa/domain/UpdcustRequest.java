package com.augment.cbsa.domain;

import java.util.Objects;

public record UpdcustRequest(
        long customerNumber,
        String name,
        String address,
        int dateOfBirth,
        int creditScore,
        int csReviewDate
) {

    public UpdcustRequest {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
    }
}
