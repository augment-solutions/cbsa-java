package com.augment.cbsa.web.dbcrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public record DbcrfunRequestDto(
        @JsonProperty("PAYDBCR")
        @Valid
        @NotNull
        DbcrfunCommareaRequestDto paydbcr
) {

    public DbcrfunRequestDto {
        Objects.requireNonNull(paydbcr, "paydbcr must not be null");
    }
}