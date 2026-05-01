package com.augment.cbsa.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

public record CustomerDetails(
        String sortcode,
        long customerNumber,
        String name,
        String address,
        LocalDate dateOfBirth,
        int creditScore,
        LocalDate csReviewDate
) {

    private static final Pattern SORTCODE_PATTERN = Pattern.compile("[0-9]{6}");

    public CustomerDetails {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");

        if (!SORTCODE_PATTERN.matcher(sortcode).matches()) {
            throw new IllegalArgumentException("sortcode must be exactly 6 ASCII digits");
        }

        if (creditScore < 0 || creditScore > 999) {
            throw new IllegalArgumentException("creditScore must be between 0 and 999");
        }
    }
}