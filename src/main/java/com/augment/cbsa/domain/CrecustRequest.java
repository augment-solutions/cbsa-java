package com.augment.cbsa.domain;

import java.util.Objects;

public record CrecustRequest(
        String name,
        String address,
        int dateOfBirth
) {

    public CrecustRequest {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
    }
}
