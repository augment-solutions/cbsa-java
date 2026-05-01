package com.augment.cbsa.web.updacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdaccResponseDto(
        @JsonProperty("UpdAcc")
        UpdaccCommareaResponseDto updAcc
) {
}