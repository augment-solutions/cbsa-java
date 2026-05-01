package com.augment.cbsa.web.delacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record DelaccResponseDto(
        @JsonProperty("DelAcc")
        DelaccCommareaResponseDto delAcc
) {

    public DelaccResponseDto {
        Objects.requireNonNull(delAcc, "delAcc must not be null");
    }
}
