package com.augment.cbsa.web.creacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreaccKeyDto(
        @JsonProperty("CommSortcode")
        @NotNull
        @Pattern(regexp = "[0-9]{6}")
        String commSortcode,

        @JsonProperty("CommNumber")
        @NotNull
        @Min(0)
        @Max(99_999_999)
        Long commNumber
) {
}