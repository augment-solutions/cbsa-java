package com.augment.cbsa.web.updcust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public record UpdcustRequestDto(
        @JsonProperty("UpdCust")
        @Valid
        @NotNull
        UpdcustCommareaRequestDto updCust
) {

    public UpdcustRequestDto {
        Objects.requireNonNull(updCust, "updCust must not be null");
    }
}
