package com.augment.cbsa.web.inqcust.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public record InqcustRequestDto(
        @PositiveOrZero
        @Max(9_999_999_999L)
        long customerNumber
) {
}