package com.augment.cbsa.web.updacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record UpdaccCommareaResponseDto(
        @JsonProperty("CommEye")
        String commEye,

        @JsonProperty("CommCustno")
        String commCustno,

        @JsonProperty("CommScode")
        String commScode,

        @JsonProperty("CommAccno")
        long commAccno,

        @JsonProperty("CommAccType")
        String commAccType,

        @JsonProperty("CommIntRate")
        BigDecimal commIntRate,

        @JsonProperty("CommOpened")
        int commOpened,

        @JsonProperty("CommOverdraft")
        long commOverdraft,

        @JsonProperty("CommLastStmtDt")
        int commLastStmtDt,

        @JsonProperty("CommNextStmtDt")
        int commNextStmtDt,

        @JsonProperty("CommAvailBal")
        BigDecimal commAvailBal,

        @JsonProperty("CommActualBal")
        BigDecimal commActualBal,

        @JsonProperty("CommSuccess")
        String commSuccess
) {
}