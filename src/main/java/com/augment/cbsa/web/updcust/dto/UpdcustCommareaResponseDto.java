package com.augment.cbsa.web.updcust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdcustCommareaResponseDto(
        @JsonProperty("CommEye")
        String commEye,

        @JsonProperty("CommScode")
        String commScode,

        @JsonProperty("CommCustno")
        String commCustno,

        @JsonProperty("CommName")
        String commName,

        @JsonProperty("CommAddress")
        String commAddress,

        @JsonProperty("CommDob")
        int commDob,

        @JsonProperty("CommCreditScore")
        int commCreditScore,

        @JsonProperty("CommCsReviewDate")
        int commCsReviewDate,

        @JsonProperty("CommUpdSuccess")
        String commUpdSuccess,

        @JsonProperty("CommUpdFailCd")
        String commUpdFailCd
) {
}
