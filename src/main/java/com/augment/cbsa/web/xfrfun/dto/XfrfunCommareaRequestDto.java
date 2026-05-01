package com.augment.cbsa.web.xfrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record XfrfunCommareaRequestDto(
        @JsonProperty("CommFaccno")
        @NotNull
        @Min(0)
        @Max(99_999_999L)
        Long commFaccno,

        @JsonProperty("CommFscode")
        @NotNull
        @Min(0)
        @Max(999_999)
        Integer commFscode,

        @JsonProperty("CommTaccno")
        @NotNull
        @Min(0)
        @Max(99_999_999L)
        Long commTaccno,

        @JsonProperty("CommTscode")
        @NotNull
        @Min(0)
        @Max(999_999)
        Integer commTscode,

        @JsonProperty("CommAmt")
        @NotNull
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commAmt
) {
}