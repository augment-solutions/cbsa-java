package com.augment.cbsa.web.delacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record DelaccCommareaResponseDto(
        @JsonProperty("DelAccEye")
        String delAccEye,

        @JsonProperty("DelAccCustno")
        String delAccCustno,

        @JsonProperty("DelAccScode")
        String delAccScode,

        @JsonProperty("DelAccAccno")
        long delAccAccno,

        @JsonProperty("DelAccAccType")
        String delAccAccType,

        @JsonProperty("DelAccIntRate")
        BigDecimal delAccIntRate,

        @JsonProperty("DelAccOpened")
        int delAccOpened,

        @JsonProperty("DelAccOverdraft")
        long delAccOverdraft,

        @JsonProperty("DelAccLastStmtDt")
        int delAccLastStmtDt,

        @JsonProperty("DelAccNextStmtDt")
        int delAccNextStmtDt,

        @JsonProperty("DelAccAvailBal")
        BigDecimal delAccAvailBal,

        @JsonProperty("DelAccActualBal")
        BigDecimal delAccActualBal,

        @JsonProperty("DelAccSuccess")
        String delAccSuccess,

        @JsonProperty("DelAccFailCd")
        String delAccFailCd,

        @JsonProperty("DelAccDelSuccess")
        String delAccDelSuccess,

        @JsonProperty("DelAccDelFailCd")
        String delAccDelFailCd,

        @JsonProperty("DelAccDelApplid")
        String delAccDelApplid,

        @JsonProperty("DelAccDelPcb1")
        String delAccDelPcb1,

        @JsonProperty("DelAccDelPcb2")
        String delAccDelPcb2,

        @JsonProperty("DelAccDelPcb3")
        String delAccDelPcb3
) {
}
