package com.augment.cbsa.web.delcus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record DelcusResponseDto(
        @JsonProperty("DelCus")
        DelcusCommareaResponseDto delCus
) {

    public DelcusResponseDto {
        Objects.requireNonNull(delCus, "delCus must not be null");
    }
}
