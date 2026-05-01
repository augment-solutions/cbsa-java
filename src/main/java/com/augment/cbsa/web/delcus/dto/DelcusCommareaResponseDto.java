package com.augment.cbsa.web.delcus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DelcusCommareaResponseDto(
        @JsonProperty("CommEye")
        String commEye,

        @JsonProperty("CommScode")
        String commScode,

        @JsonProperty("CommCustno")
        String commCustno,

        @JsonProperty("CommName")
        String commName,

        @JsonProperty("CommAddr")
        String commAddr,

        @JsonProperty("CommDob")
        int commDob,

        @JsonProperty("CommCreditScore")
        int commCreditScore,

        @JsonProperty("CommCsReviewDate")
        int commCsReviewDate,

        @JsonProperty("CommDelSuccess")
        String commDelSuccess,

        @JsonProperty("CommDelFailCd")
        String commDelFailCd
) {
}
