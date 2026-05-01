package com.augment.cbsa.web.crecust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CrecustKeyDto(
        @JsonProperty("CommSortcode")
        @NotNull
        @Pattern(regexp = "[0-9]{6}")
        String commSortcode,

        @JsonProperty("CommNumber")
        @NotNull
        @Min(0)
        @Max(9_999_999_999L)
        Long commNumber
) {
}
