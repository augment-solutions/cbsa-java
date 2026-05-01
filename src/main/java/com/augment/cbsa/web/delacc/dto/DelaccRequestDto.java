package com.augment.cbsa.web.delacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DelaccRequestDto(
        @JsonProperty("DelAcc")
        @Valid
        @NotNull
        DelaccCommareaRequestDto delAcc
) {
}
