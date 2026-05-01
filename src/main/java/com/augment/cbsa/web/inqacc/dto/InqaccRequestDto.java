package com.augment.cbsa.web.inqacc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public record InqaccRequestDto(
        @PositiveOrZero
        @Max(99_999_999L)
        long accountNumber
) {
}