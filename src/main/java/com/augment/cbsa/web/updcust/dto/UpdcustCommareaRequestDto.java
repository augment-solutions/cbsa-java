package com.augment.cbsa.web.updcust.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public record UpdcustCommareaRequestDto(
        @JsonProperty("CommEye")
        @Size(max = 4)
        String commEye,

        @JsonProperty("CommScode")
        @NotNull
        @Pattern(regexp = "[0-9]{1,6}")
        String commScode,

        @JsonProperty("CommCustno")
        @NotNull
        @Pattern(regexp = "[0-9]{1,10}")
        String commCustno,

        @JsonProperty("CommName")
        @NotNull
        @Size(max = 60)
        String commName,

        @JsonProperty("CommAddress")
        @NotNull
        @Size(max = 160)
        String commAddress,

        @JsonProperty("CommDob")
        @NotNull
        @Min(0)
        @Max(99_999_999)
        Integer commDob,

        @JsonProperty("CommCreditScore")
        @NotNull
        @Min(0)
        @Max(999)
        Integer commCreditScore,

        @JsonProperty("CommCsReviewDate")
        @NotNull
        @Min(0)
        @Max(99_999_999)
        Integer commCsReviewDate,

        @JsonProperty("CommUpdSuccess")
        @Size(max = 1)
        String commUpdSuccess,

        @JsonProperty("CommUpdFailCd")
        @Size(max = 1)
        String commUpdFailCd
) {

    public UpdcustCommareaRequestDto {
        Objects.requireNonNull(commScode, "commScode must not be null");
        Objects.requireNonNull(commCustno, "commCustno must not be null");
        Objects.requireNonNull(commName, "commName must not be null");
        Objects.requireNonNull(commAddress, "commAddress must not be null");
        Objects.requireNonNull(commDob, "commDob must not be null");
        Objects.requireNonNull(commCreditScore, "commCreditScore must not be null");
        Objects.requireNonNull(commCsReviewDate, "commCsReviewDate must not be null");
    }
}
