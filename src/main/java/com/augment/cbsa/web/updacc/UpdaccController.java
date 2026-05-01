package com.augment.cbsa.web.updacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.UpdaccRequest;
import com.augment.cbsa.domain.UpdaccResult;
import com.augment.cbsa.service.UpdaccService;
import com.augment.cbsa.web.updacc.dto.UpdaccCommareaResponseDto;
import com.augment.cbsa.web.updacc.dto.UpdaccRequestDto;
import com.augment.cbsa.web.updacc.dto.UpdaccResponseDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/updacc")
public class UpdaccController {

    private static final String EYE_CATCHER = "ACCT";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final UpdaccService updaccService;

    public UpdaccController(UpdaccService updaccService) {
        this.updaccService = Objects.requireNonNull(updaccService, "updaccService must not be null");
    }

    @PutMapping("/update")
    public ResponseEntity<?> update(@Valid @RequestBody UpdaccRequestDto requestDto) {
        UpdaccRequest request = new UpdaccRequest(
                requestDto.updAcc().commAccno(),
                requestDto.updAcc().commAccType(),
                requestDto.updAcc().commIntRate(),
                requestDto.updAcc().commOverdraft()
        );
        UpdaccResult result = updaccService.update(request);

        if (!result.updateSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(UpdaccResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isValidationFailure()) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(UpdaccResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(UpdaccResult result) {
        if (result.isNotFoundFailure()) {
            return "Account not found";
        }
        if (result.isValidationFailure()) {
            return "Invalid account type";
        }
        return "Account update failed";
    }

    private UpdaccResponseDto toResponse(UpdaccResult result) {
        AccountDetails account = Objects.requireNonNull(result.account(), "Successful response requires an account");
        return new UpdaccResponseDto(new UpdaccCommareaResponseDto(
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
                "Y"
        ));
    }

    private int toCobolDate(LocalDate date) {
        if (date == null) {
            return 0;
        }
        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}