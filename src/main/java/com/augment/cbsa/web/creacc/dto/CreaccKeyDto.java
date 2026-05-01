package com.augment.cbsa.web.creacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public record CreaccKeyDto(
        @JsonProperty("CommSortcode")
        @NotNull
        @Min(0)
        @Max(999_999)
        Integer commSortcode,

        @JsonProperty("CommNumber")
        @NotNull
        @Min(0)
        @Max(99_999_999)
        Long commNumber
) {

    public CreaccKeyDto {
        Objects.requireNonNull(commSortcode, "commSortcode must not be null");
        Objects.requireNonNull(commNumber, "commNumber must not be null");
    }
}