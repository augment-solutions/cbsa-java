package com.augment.cbsa.web.dbcrfun.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record DbcrfunOriginDto(
        @JsonProperty("CommApplid")
        @Size(max = 8)
        String commApplid,

        @JsonProperty("CommUserid")
        @Size(max = 8)
        String commUserid,

        @JsonProperty("CommFacilityName")
        @Size(max = 8)
        String commFacilityName,

        @JsonProperty("CommNetwrkId")
        @Size(max = 8)
        String commNetwrkId,

        @JsonProperty("CommFaciltype")
        @Min(-99_999_999)
        @Max(99_999_999)
        Integer commFaciltype,

        @JsonProperty("Fill0")
        @Size(max = 4)
        String fill0
) {
}