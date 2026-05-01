package com.augment.cbsa.web.dbcrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DbcrfunRequestDto(
        @JsonProperty("PAYDBCR")
        @Valid
        @NotNull
        DbcrfunCommareaRequestDto paydbcr
) {
}