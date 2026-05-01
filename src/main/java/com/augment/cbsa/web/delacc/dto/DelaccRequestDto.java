package com.augment.cbsa.web.delacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public record DelaccRequestDto(
        @JsonProperty("DelAcc")
        @Valid
        @NotNull
        DelaccCommareaRequestDto delAcc
) {

    public DelaccRequestDto {
        Objects.requireNonNull(delAcc, "delAcc must not be null");
    }
}
