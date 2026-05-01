package com.augment.cbsa.web.creacc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Objects;

public record CreaccCommareaRequestDto(
        @JsonProperty("CommEyecatcher")
        @Size(max = 4)
        String commEyecatcher,

        @JsonProperty("CommCustno")
        @NotNull
        @Min(0)
        @Max(9_999_999_999L)
        Long commCustno,

        @JsonProperty("CommKey")
        @Valid
        @NotNull
        CreaccKeyDto commKey,

        @JsonProperty("CommAccType")
        @NotNull
        @Size(max = 8)
        String commAccType,

        @JsonProperty("CommIntRt")
        @NotNull
        @Digits(integer = 4, fraction = 2)
        @DecimalMin("0.00")
        @DecimalMax("9999.99")
        BigDecimal commIntRt,

        @JsonProperty("CommOpened")
        @Min(0)
        @Max(99_999_999)
        Integer commOpened,

        @JsonProperty("CommOverdrLim")
        @NotNull
        @Min(0)
        @Max(99_999_999)
        Long commOverdrLim,

        @JsonProperty("CommLastStmtDt")
        @Min(0)
        @Max(99_999_999)
        Integer commLastStmtDt,

        @JsonProperty("CommNextStmtDt")
        @Min(0)
        @Max(99_999_999)
        Integer commNextStmtDt,

        @JsonProperty("CommAvailBal")
        @NotNull
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commAvailBal,

        @JsonProperty("CommActBal")
        @NotNull
        @Digits(integer = 10, fraction = 2)
        @DecimalMin("-9999999999.99")
        @DecimalMax("9999999999.99")
        BigDecimal commActBal,

        @JsonProperty("CommSuccess")
        @Size(max = 1)
        String commSuccess,

        @JsonProperty("CommFailCode")
        @Size(max = 1)
        String commFailCode
) {

    public CreaccCommareaRequestDto {
        Objects.requireNonNull(commCustno, "commCustno must not be null");
        Objects.requireNonNull(commKey, "commKey must not be null");
        Objects.requireNonNull(commAccType, "commAccType must not be null");
        Objects.requireNonNull(commIntRt, "commIntRt must not be null");
        Objects.requireNonNull(commOverdrLim, "commOverdrLim must not be null");
        Objects.requireNonNull(commAvailBal, "commAvailBal must not be null");
        Objects.requireNonNull(commActBal, "commActBal must not be null");
    }
}