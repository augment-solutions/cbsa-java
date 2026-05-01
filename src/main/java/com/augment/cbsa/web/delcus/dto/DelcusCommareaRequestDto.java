package com.augment.cbsa.web.delcus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DelcusCommareaRequestDto(
        @JsonProperty("CommEye")
        @Size(max = 4)
        String commEye,

        @JsonProperty("CommScode")
        @Pattern(regexp = "[0-9]{0,6}")
        String commScode,

        @JsonProperty("CommCustno")
        @Pattern(regexp = "[0-9]{0,10}")
        String commCustno,

        @JsonProperty("CommName")
        @Size(max = 60)
        String commName,

        @JsonProperty("CommAddr")
        @Size(max = 160)
        String commAddr,

        @JsonProperty("CommDob")
        @Min(0)
        @Max(99_999_999)
        Integer commDob,

        @JsonProperty("CommCreditScore")
        @Min(0)
        @Max(999)
        Integer commCreditScore,

        @JsonProperty("CommCsReviewDate")
        @Min(0)
        @Max(99_999_999)
        Integer commCsReviewDate,

        @JsonProperty("CommDelSuccess")
        @Size(max = 1)
        String commDelSuccess,

        @JsonProperty("CommDelFailCd")
        @Size(max = 1)
        String commDelFailCd
) {
}
