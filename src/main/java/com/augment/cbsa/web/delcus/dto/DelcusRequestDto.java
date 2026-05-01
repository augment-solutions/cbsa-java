package com.augment.cbsa.web.delcus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DelcusRequestDto(
        @JsonProperty("DelCus")
        @Valid
        @NotNull
        DelcusCommareaRequestDto delCus
) {
}
