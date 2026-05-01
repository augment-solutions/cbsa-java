package com.augment.cbsa.web.crecust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record CrecustResponseDto(
        @JsonProperty("CreCust")
        CrecustCommareaResponseDto creCust
) {

    public CrecustResponseDto {
        Objects.requireNonNull(creCust, "creCust must not be null");
    }
}
