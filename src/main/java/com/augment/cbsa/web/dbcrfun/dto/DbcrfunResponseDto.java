package com.augment.cbsa.web.dbcrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record DbcrfunResponseDto(
        @JsonProperty("PAYDBCR")
        DbcrfunCommareaResponseDto paydbcr
) {

    public DbcrfunResponseDto {
        Objects.requireNonNull(paydbcr, "paydbcr must not be null");
    }
}