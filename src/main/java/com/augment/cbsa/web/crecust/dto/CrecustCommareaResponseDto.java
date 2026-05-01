package com.augment.cbsa.web.crecust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrecustCommareaResponseDto(
        @JsonProperty("CommEyecatcher")
        String commEyecatcher,

        @JsonProperty("CommKey")
        CrecustKeyDto commKey,

        @JsonProperty("CommName")
        String commName,

        @JsonProperty("CommAddress")
        String commAddress,

        @JsonProperty("CommDateOfBirth")
        int commDateOfBirth,

        @JsonProperty("CommCreditScore")
        int commCreditScore,

        @JsonProperty("CommCsReviewDate")
        int commCsReviewDate,

        @JsonProperty("CommSuccess")
        String commSuccess,

        @JsonProperty("CommFailCode")
        String commFailCode
) {
}
