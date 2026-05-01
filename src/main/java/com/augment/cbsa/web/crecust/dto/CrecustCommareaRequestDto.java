package com.augment.cbsa.web.crecust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrecustCommareaRequestDto(
        @JsonProperty("CommEyecatcher")
        @Size(max = 4)
        String commEyecatcher,

        @JsonProperty("CommKey")
        @Valid
        @NotNull
        CrecustKeyDto commKey,

        @JsonProperty("CommName")
        @NotBlank
        @Size(max = 60)
        String commName,

        @JsonProperty("CommAddress")
        @NotNull
        @Size(max = 160)
        String commAddress,

        @JsonProperty("CommDateOfBirth")
        @NotNull
        @Min(0)
        @Max(99_999_999)
        Integer commDateOfBirth,

        @JsonProperty("CommCreditScore")
        @Min(0)
        @Max(999)
        Integer commCreditScore,

        @JsonProperty("CommCsReviewDate")
        @Min(0)
        @Max(99_999_999)
        Integer commCsReviewDate,

        @JsonProperty("CommSuccess")
        @Size(max = 1)
        String commSuccess,

        @JsonProperty("CommFailCode")
        @Size(max = 1)
        String commFailCode
) {
}
