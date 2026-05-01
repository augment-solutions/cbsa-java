package com.augment.cbsa.web.inqacccu.dto;

import java.util.List;

public record InqacccuResponseDto(
        long customerNumber,
        String inquirySuccess,
        String failCode,
        String customerFound,
        String pcbPointer,
        List<InqacccuAccountDto> accountDetails
) {
}