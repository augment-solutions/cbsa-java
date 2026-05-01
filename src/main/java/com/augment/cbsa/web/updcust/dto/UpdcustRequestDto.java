package com.augment.cbsa.web.updcust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdcustRequestDto(
        @JsonProperty("UpdCust")
        @Valid
        @NotNull
        UpdcustCommareaRequestDto updCust
) {
}
