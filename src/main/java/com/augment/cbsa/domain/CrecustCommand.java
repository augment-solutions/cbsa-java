package com.augment.cbsa.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public record CrecustCommand(
        String sortcode,
        String name,
        String address,
        LocalDate dateOfBirth,
        int creditScore,
        LocalDate reviewDate,
        long transactionReference,
        LocalDate transactionDate,
        LocalTime transactionTime
) {

    public CrecustCommand {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(reviewDate, "reviewDate must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");

        if (creditScore < 0 || creditScore > 999) {
            throw new IllegalArgumentException("creditScore must be between 0 and 999");
        }
    }
}
