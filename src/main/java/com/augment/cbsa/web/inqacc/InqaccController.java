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
import java.util.Locale;
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

    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);
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

        if (!result.inquirySuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(InqaccResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isRandomRetryExhaustedFailure()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(InqaccResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(InqaccResult result) {
        if (result.isNotFoundFailure()) {
            return "Account not found";
        }
        if (result.isRandomRetryExhaustedFailure()) {
            return "Account lookup retry exhausted";
        }
        return "Account inquiry failed";
    }

    private InqaccResponseDto toResponse(InqaccResult result) {
        AccountDetails account = Objects.requireNonNull(result.account(), "Successful response requires an account");
        return new InqaccResponseDto(
                EYE_CATCHER,
                account.customerNumber(),
                account.sortcode(),
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