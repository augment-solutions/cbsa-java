package com.augment.cbsa.web.updacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdaccCommareaRequestDto(
        @JsonProperty("CommEye")
        @Size(max = 4)
        String commEye,

        @JsonProperty("CommCustno")
        @Pattern(regexp = "[0-9]{1,10}")
        String commCustno,

        @JsonProperty("CommScode")
        @Pattern(regexp = "[0-9]{1,6}")
        String commScode,

        @JsonProperty("CommAccno")
        @NotNull
        @PositiveOrZero
        @Max(99_999_999L)
        Long commAccno,

        @JsonProperty("CommAccType")
        @NotNull
        @Size(max = 8)
        String commAccType,

        @JsonProperty("CommIntRate")
        @NotNull
        @Digits(integer = 4, fraction = 2)
        @DecimalMin("0.00")
        @DecimalMax("9999.99")
        BigDecimal commIntRate,

        @JsonProperty("CommOpened")
        @PositiveOrZero
        @Max(99_999_999L)
        Integer commOpened,

        @JsonProperty("CommOverdraft")
        @NotNull
        @PositiveOrZero
        @Max(99_999_999L)
        Long commOverdraft,

        @JsonProperty("CommLastStmtDt")
        @PositiveOrZero
        @Max(99_999_999L)
        Integer commLastStmtDt,

        @JsonProperty("CommNextStmtDt")
        @PositiveOrZero
        @Max(99_999_999L)
        Integer commNextStmtDt,

        @JsonProperty("CommAvailBal")
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commAvailBal,

        @JsonProperty("CommActualBal")
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commActualBal,

        @JsonProperty("CommSuccess")
        @Size(max = 1)
        String commSuccess
) {
}