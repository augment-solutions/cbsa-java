package com.augment.cbsa.web.inqacccu;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.service.InqacccuService;
import com.augment.cbsa.web.inqacccu.dto.InqacccuAccountDto;
import com.augment.cbsa.web.inqacccu.dto.InqacccuRequestDto;
import com.augment.cbsa.web.inqacccu.dto.InqacccuResponseDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
@RequestMapping("/api/v1/inqacccu")
public class InqacccuController {

    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);
    private static final String EYE_CATCHER = "ACCT";

    private final InqacccuService inqacccuService;

    public InqacccuController(InqacccuService inqacccuService) {
        this.inqacccuService = inqacccuService;
    }

    @GetMapping("/{customerNumber}")
    public ResponseEntity<?> inquire(
            @PathVariable
            @PositiveOrZero
            @Max(9_999_999_999L)
            long customerNumber
    ) {
        InqacccuRequestDto requestDto = new InqacccuRequestDto(customerNumber);
        InqacccuResult result = inqacccuService.inquire(new InqacccuRequest(requestDto.customerNumber()));

        if (!result.inquirySuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(InqacccuResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isRandomRetryExhaustedFailure()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(InqacccuResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(InqacccuResult result) {
        if (result.isNotFoundFailure()) {
            return "Customer not found";
        }
        if (result.isRandomRetryExhaustedFailure()) {
            return "Customer account inquiry retry exhausted";
        }
        return "Customer account inquiry failed";
    }

    private InqacccuResponseDto toResponse(InqacccuResult result) {
        List<InqacccuAccountDto> accountDetails = result.accounts().stream()
                .map(this::toAccountDto)
                .toList();

        return new InqacccuResponseDto(
                result.customerNumber(),
                "Y",
                result.failCode(),
                result.customerFound() ? "Y" : "N",
                "",
                accountDetails
        );
    }

    private InqacccuAccountDto toAccountDto(AccountDetails account) {
        return new InqacccuAccountDto(
                EYE_CATCHER,
                account.customerNumber(),
                account.sortcode(),
                account.accountNumber(),
                account.accountType(),
                account.interestRate(),
                toCobolDate(account.opened()),
                account.overdraftLimit().toBigInteger().longValueExact(),
                toCobolDate(account.lastStatementDate()),
                toCobolDate(account.nextStatementDate()),
                account.availableBalance(),
                account.actualBalance()
        );
    }

    private int toCobolDate(LocalDate date) {
        if (date == null) {
            return 0;
        }

        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}