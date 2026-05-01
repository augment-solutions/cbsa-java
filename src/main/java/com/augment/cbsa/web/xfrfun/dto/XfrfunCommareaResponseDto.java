package com.augment.cbsa.web.xfrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record XfrfunCommareaResponseDto(
        @JsonProperty("CommFaccno")
        long commFaccno,

        @JsonProperty("CommFscode")
        String commFscode,

        @JsonProperty("CommTaccno")
        long commTaccno,

        @JsonProperty("CommTscode")
        String commTscode,

        @JsonProperty("CommAmt")
        BigDecimal commAmt,

        @JsonProperty("CommFavbal")
        BigDecimal commFavbal,

        @JsonProperty("CommFactbal")
        BigDecimal commFactbal,

        @JsonProperty("CommTavbal")
        BigDecimal commTavbal,

        @JsonProperty("CommTactbal")
        BigDecimal commTactbal,

        @JsonProperty("CommFailCode")
        String commFailCode,

        @JsonProperty("CommSuccess")
        String commSuccess
) {
}