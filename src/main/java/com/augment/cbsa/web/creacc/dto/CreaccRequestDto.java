package com.augment.cbsa.web.creacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreaccRequestDto(
        @JsonProperty("CreAcc")
        @Valid
        @NotNull
        CreaccCommareaRequestDto creAcc
) {
}