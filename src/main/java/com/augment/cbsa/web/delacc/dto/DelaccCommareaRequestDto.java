package com.augment.cbsa.web.delacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DelaccCommareaRequestDto(
        @JsonProperty("DelAccEye")
        @Size(max = 4)
        String delAccEye,

        @JsonProperty("DelAccCustno")
        @Pattern(regexp = "[0-9]{0,10}")
        String delAccCustno,

        @JsonProperty("DelAccScode")
        @Pattern(regexp = "[0-9]{0,6}")
        String delAccScode,

        @JsonProperty("DelAccAccno")
        @PositiveOrZero
        @Max(99_999_999L)
        Long delAccAccno,

        @JsonProperty("DelAccAccType")
        @Size(max = 8)
        String delAccAccType,

        @JsonProperty("DelAccIntRate")
        @Digits(integer = 4, fraction = 2)
        @DecimalMin("0.00")
        @DecimalMax("9999.99")
        BigDecimal delAccIntRate,

        @JsonProperty("DelAccOpened")
        @PositiveOrZero
        @Max(99_999_999L)
        Integer delAccOpened,

        @JsonProperty("DelAccOverdraft")
        @PositiveOrZero
        @Max(99_999_999L)
        Long delAccOverdraft,

        @JsonProperty("DelAccLastStmtDt")
        @PositiveOrZero
        @Max(99_999_999L)
        Integer delAccLastStmtDt,

        @JsonProperty("DelAccNextStmtDt")
        @PositiveOrZero
        @Max(99_999_999L)
        Integer delAccNextStmtDt,

        @JsonProperty("DelAccAvailBal")
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal delAccAvailBal,

        @JsonProperty("DelAccActualBal")
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal delAccActualBal,

        @JsonProperty("DelAccSuccess")
        @Size(max = 1)
        String delAccSuccess,

        @JsonProperty("DelAccFailCd")
        @Size(max = 1)
        String delAccFailCd,

        @JsonProperty("DelAccDelSuccess")
        @Size(max = 1)
        String delAccDelSuccess,

        @JsonProperty("DelAccDelFailCd")
        @Size(max = 1)
        String delAccDelFailCd,

        @JsonProperty("DelAccDelApplid")
        @Size(max = 8)
        String delAccDelApplid,

        @JsonProperty("DelAccDelPcb1")
        @Size(max = 4)
        String delAccDelPcb1,

        @JsonProperty("DelAccDelPcb2")
        @Size(max = 4)
        String delAccDelPcb2,

        @JsonProperty("DelAccDelPcb3")
        @Size(max = 4)
        String delAccDelPcb3
) {
}
