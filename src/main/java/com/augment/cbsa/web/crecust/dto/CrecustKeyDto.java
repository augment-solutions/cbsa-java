package com.augment.cbsa.web.crecust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

public record CrecustKeyDto(
        @JsonProperty("CommSortcode")
        @NotNull
        @Pattern(regexp = "\\d{6}")
        String commSortcode,

        @JsonProperty("CommNumber")
        @NotNull
        @Min(0)
        @Max(9_999_999_999L)
        Long commNumber
) {

    public CrecustKeyDto {
        Objects.requireNonNull(commSortcode, "commSortcode must not be null");
        Objects.requireNonNull(commNumber, "commNumber must not be null");
    }
}
