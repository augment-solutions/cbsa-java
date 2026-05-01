package com.augment.cbsa.web.updcust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record UpdcustResponseDto(
        @JsonProperty("UpdCust")
        UpdcustCommareaResponseDto updCust
) {

    public UpdcustResponseDto {
        Objects.requireNonNull(updCust, "updCust must not be null");
    }
}
