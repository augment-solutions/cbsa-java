package com.augment.cbsa.domain;

import java.time.LocalDate;
import java.util.Objects;

public record CustomerDetails(
        String sortcode,
        long customerNumber,
        String name,
        String address,
        LocalDate dateOfBirth,
        int creditScore,
        LocalDate csReviewDate
) {

    public CustomerDetails {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");

        if (creditScore < 0 || creditScore > 999) {
            throw new IllegalArgumentException("creditScore must be between 0 and 999");
        }
    }
}