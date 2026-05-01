package com.augment.cbsa.web.crecust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CrecustRequestDto(
        @JsonProperty("CreCust")
        @Valid
        @NotNull
        CrecustCommareaRequestDto creCust
) {
}
