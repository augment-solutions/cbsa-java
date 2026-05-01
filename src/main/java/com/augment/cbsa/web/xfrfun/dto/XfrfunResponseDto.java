package com.augment.cbsa.web.xfrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record XfrfunResponseDto(
        @JsonProperty("XFRFUN")
        XfrfunCommareaResponseDto xfrfun
) {

    public XfrfunResponseDto {
        Objects.requireNonNull(xfrfun, "xfrfun must not be null");
    }
}