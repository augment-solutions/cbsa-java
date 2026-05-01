package com.augment.cbsa.web.creacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CreaccCommareaResponseDto(
        @JsonProperty("CommEyecatcher")
        String commEyecatcher,

        @JsonProperty("CommCustno")
        long commCustno,

        @JsonProperty("CommKey")
        CreaccKeyDto commKey,

        @JsonProperty("CommAccType")
        String commAccType,

        @JsonProperty("CommIntRt")
        BigDecimal commIntRt,

        @JsonProperty("CommOpened")
        int commOpened,

        @JsonProperty("CommOverdrLim")
        long commOverdrLim,

        @JsonProperty("CommLastStmtDt")
        int commLastStmtDt,

        @JsonProperty("CommNextStmtDt")
        int commNextStmtDt,

        @JsonProperty("CommAvailBal")
        BigDecimal commAvailBal,

        @JsonProperty("CommActBal")
        BigDecimal commActBal,

        @JsonProperty("CommSuccess")
        String commSuccess,

        @JsonProperty("CommFailCode")
        String commFailCode
) {
}