package com.augment.cbsa.web.inqcust.dto;

public record InqcustResponseDto(
        String eye,
        String sortcode,
        long customerNumber,
        String name,
        String address,
        InqcustDateDto dateOfBirth,
        int creditScore,
        InqcustDateDto creditScoreReviewDate,
        String inquirySuccess,
        String failCode,
        String pcbPointer
) {
}