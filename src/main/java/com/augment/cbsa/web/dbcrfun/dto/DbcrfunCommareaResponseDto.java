package com.augment.cbsa.web.dbcrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record DbcrfunCommareaResponseDto(
        @JsonProperty("CommAccno")
        String commAccno,

        @JsonProperty("CommAmt")
        BigDecimal commAmt,

        @JsonProperty("mSortC")
        String mSortC,

        @JsonProperty("CommAvBal")
        BigDecimal commAvBal,

        @JsonProperty("CommActBal")
        BigDecimal commActBal,

        @JsonProperty("CommOrigin")
        DbcrfunOriginDto commOrigin,

        @JsonProperty("CommSuccess")
        String commSuccess,

        @JsonProperty("CommFailCode")
        String commFailCode
) {
}