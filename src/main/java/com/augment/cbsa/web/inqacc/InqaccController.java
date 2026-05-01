package com.augment.cbsa.web.inqacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqaccRequest;
import com.augment.cbsa.domain.InqaccResult;
import com.augment.cbsa.service.InqaccService;
import com.augment.cbsa.web.inqacc.dto.InqaccRequestDto;
import com.augment.cbsa.web.inqacc.dto.InqaccResponseDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/inqacc")
public class InqaccController {

    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final String EYE_CATCHER = "ACCT";

    private final InqaccService inqaccService;

    public InqaccController(InqaccService inqaccService) {
        this.inqaccService = inqaccService;
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<?> inquire(
            @PathVariable
            @PositiveOrZero
            @Max(99_999_999L)
            long accountNumber
    ) {
        InqaccRequestDto requestDto = new InqaccRequestDto(accountNumber);
        InqaccResult result = inqaccService.inquire(new InqaccRequest(requestDto.accountNumber()));

        if (result.isNotFoundFailure()) {
            ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            problemDetail.setTitle("Account not found");
            problemDetail.setDetail(result.message());
            problemDetail.setProperty("failCode", result.failCode());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private InqaccResponseDto toResponse(InqaccResult result) {
        AccountDetails account = Objects.requireNonNull(result.account(), "Successful response requires an account");
        return new InqaccResponseDto(
                EYE_CATCHER,
                account.customerNumber(),
                Integer.parseInt(account.sortcode()),
                account.accountNumber(),
                account.accountType(),
                account.interestRate(),
                toCobolDate(account.opened()),
                account.overdraftLimit().longValueExact(),
                toCobolDate(account.lastStatementDate()),
                toCobolDate(account.nextStatementDate()),
                account.availableBalance(),
                account.actualBalance(),
                "Y",
                ""
        );
    }

    private int toCobolDate(LocalDate date) {
        if (date == null) {
            return 0;
        }

        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}