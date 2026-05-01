package com.augment.cbsa.web.inqacccu.dto;

import java.math.BigDecimal;

public record InqacccuAccountDto(
        String eye,
        long customerNumber,
        String sortcode,
        long accountNumber,
        String accountType,
        BigDecimal interestRate,
        int opened,
        long overdraft,
        int lastStatementDate,
        int nextStatementDate,
        BigDecimal availableBalance,
        BigDecimal actualBalance
) {
}