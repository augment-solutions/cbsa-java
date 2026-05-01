package com.augment.cbsa.web.updacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdaccRequestDto(
        @JsonProperty("UpdAcc")
        @Valid
        @NotNull
        UpdaccCommareaRequestDto updAcc
) {
}