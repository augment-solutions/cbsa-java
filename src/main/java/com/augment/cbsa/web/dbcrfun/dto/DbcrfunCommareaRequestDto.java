package com.augment.cbsa.web.dbcrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DbcrfunCommareaRequestDto(
        @JsonProperty("CommAccno")
        @NotNull
        @Pattern(regexp = "[0-9]{1,8}")
        String commAccno,

        @JsonProperty("CommAmt")
        @NotNull
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commAmt,

        @JsonProperty("mSortC")
        @Pattern(regexp = "\\d{6}")
        String mSortC,

        @JsonProperty("CommAvBal")
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commAvBal,

        @JsonProperty("CommActBal")
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commActBal,

        @JsonProperty("CommOrigin")
        @Valid
        @NotNull
        DbcrfunOriginDto commOrigin,

        @JsonProperty("CommSuccess")
        @Size(max = 1)
        String commSuccess,

        @JsonProperty("CommFailCode")
        @Size(max = 1)
        String commFailCode
) {
}