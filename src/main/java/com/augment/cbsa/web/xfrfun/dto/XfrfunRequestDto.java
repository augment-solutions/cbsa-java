package com.augment.cbsa.web.xfrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record XfrfunRequestDto(
        @JsonProperty("XFRFUN")
        @Valid
        @NotNull
        XfrfunCommareaRequestDto xfrfun
) {
}