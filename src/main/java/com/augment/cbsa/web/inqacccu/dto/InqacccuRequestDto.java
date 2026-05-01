package com.augment.cbsa.web.inqacccu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public record InqacccuRequestDto(
        @PositiveOrZero
        @Max(9_999_999_999L)
        long customerNumber
) {
}