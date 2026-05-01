package com.augment.cbsa.web.inqacc.dto;

import java.math.BigDecimal;

public record InqaccResponseDto(
        String eye,
        long customerNumber,
        int sortcode,
        long accountNumber,
        String accountType,
        BigDecimal interestRate,
        int opened,
        long overdraft,
        int lastStatementDate,
        int nextStatementDate,
        BigDecimal availableBalance,
        BigDecimal actualBalance,
        String inquirySuccess,
        String pcb1Pointer
) {
}