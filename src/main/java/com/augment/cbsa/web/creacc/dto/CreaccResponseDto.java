package com.augment.cbsa.web.creacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record CreaccResponseDto(
        @JsonProperty("CreAcc")
        CreaccCommareaResponseDto creAcc
) {

    public CreaccResponseDto {
        Objects.requireNonNull(creAcc, "creAcc must not be null");
    }
}