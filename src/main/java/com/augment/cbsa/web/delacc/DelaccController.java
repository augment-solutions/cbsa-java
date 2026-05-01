package com.augment.cbsa.web.delacc;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DelaccRequest;
import com.augment.cbsa.domain.DelaccResult;
import com.augment.cbsa.service.DelaccService;
import com.augment.cbsa.web.delacc.dto.DelaccCommareaRequestDto;
import com.augment.cbsa.web.delacc.dto.DelaccCommareaResponseDto;
import com.augment.cbsa.web.delacc.dto.DelaccRequestDto;
import com.augment.cbsa.web.delacc.dto.DelaccResponseDto;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/delacc")
public class DelaccController {

    private static final String EYE_CATCHER = "ACCT";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final DelaccService delaccService;
    private final CbsaProperties cbsaProperties;

    public DelaccController(DelaccService delaccService, CbsaProperties cbsaProperties) {
        this.delaccService = Objects.requireNonNull(delaccService, "delaccService must not be null");
        this.cbsaProperties = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null");
    }

    @DeleteMapping("/remove/{accno}")
    public ResponseEntity<?> delete(
            @PathVariable("accno")
            @PositiveOrZero
            @Max(99_999_999L)
            long accountNumber,
            @Valid @RequestBody DelaccRequestDto requestDto
    ) {
        Objects.requireNonNull(requestDto, "requestDto must not be null");

        // The body's DelAccAccno and DelAccScode are optional (patterns allow ""
        // / null). When present they must agree with the path account number
        // and the configured branch sortcode so a misaddressed request can
        // never silently delete the path target.
        ProblemDetail mismatch = validateBodyAgainstPath(requestDto.delAcc(), accountNumber);
        if (mismatch != null) {
            return ResponseEntity.badRequest().body(mismatch);
        }

        DelaccResult result = delaccService.delete(new DelaccRequest(accountNumber));
        if (!result.deleteSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private ProblemDetail validateBodyAgainstPath(DelaccCommareaRequestDto delAcc, long pathAccountNumber) {
        Long bodyAccno = delAcc.delAccAccno();
        if (bodyAccno != null && bodyAccno != pathAccountNumber) {
            return mismatch("Body DelAccAccno does not match path accno.");
        }
        String bodyScode = delAcc.delAccScode();
        if (bodyScode != null && !bodyScode.isEmpty()
                && !bodyScode.equals(cbsaProperties.sortcode())) {
            return mismatch("Body DelAccScode does not match the configured branch sortcode.");
        }
        return null;
    }

    private ProblemDetail mismatch(String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation failed");
        problemDetail.setDetail(detail);
        return problemDetail;
    }

    private HttpStatus failureStatus(DelaccResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(DelaccResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(DelaccResult result) {
        if (result.isNotFoundFailure()) {
            return "Account not found";
        }
        return "Account deletion failed";
    }

    private DelaccResponseDto toResponse(DelaccResult result) {
        AccountDetails account = Objects.requireNonNull(result.account(), "Successful response requires an account");
        return new DelaccResponseDto(new DelaccCommareaResponseDto(
                EYE_CATCHER,
                String.format(Locale.ROOT, "%010d", account.customerNumber()),
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
                "0",
                "Y",
                "",
                "",
                "",
                "",
                ""
        ));
    }

    private int toCobolDate(LocalDate date) {
        if (date == null) {
            return 0;
        }
        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}
