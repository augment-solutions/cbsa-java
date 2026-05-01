package com.augment.cbsa.web.creacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CreaccRequest;
import com.augment.cbsa.domain.CreaccResult;
import com.augment.cbsa.service.CreaccService;
import com.augment.cbsa.web.creacc.dto.CreaccCommareaResponseDto;
import com.augment.cbsa.web.creacc.dto.CreaccKeyDto;
import com.augment.cbsa.web.creacc.dto.CreaccRequestDto;
import com.augment.cbsa.web.creacc.dto.CreaccResponseDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/creacc")
public class CreaccController {

    private static final String EYE_CATCHER = "ACCT";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final CreaccService creaccService;

    public CreaccController(CreaccService creaccService) {
        this.creaccService = Objects.requireNonNull(creaccService, "creaccService must not be null");
    }

    @PostMapping("/insert")
    public ResponseEntity<?> create(@Valid @RequestBody CreaccRequestDto requestDto) {
        CreaccRequest request = new CreaccRequest(
                requestDto.creAcc().commCustno(),
                requestDto.creAcc().commAccType(),
                requestDto.creAcc().commIntRt(),
                requestDto.creAcc().commOverdrLim(),
                requestDto.creAcc().commAvailBal(),
                requestDto.creAcc().commActBal()
        );
        CreaccResult result = creaccService.create(request);

        if (!result.creationSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(CreaccResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isValidationFailure()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (result.isCapacityFailure()) {
            return HttpStatus.CONFLICT;
        }
        if (result.isTransientFailure()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(CreaccResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(CreaccResult result) {
        return switch (result.failCode()) {
            case "1" -> "Customer not found";
            case "8" -> "Maximum account count reached";
            case "9" -> "Customer account count failed";
            case "A" -> "Invalid account type";
            default -> "Account creation failed";
        };
    }

    private CreaccResponseDto toResponse(CreaccResult result) {
        AccountDetails account = Objects.requireNonNull(result.account(), "Successful response requires an account");
        return new CreaccResponseDto(new CreaccCommareaResponseDto(
                EYE_CATCHER,
                account.customerNumber(),
                new CreaccKeyDto(account.sortcode(), account.accountNumber()),
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
        ));
    }

    private int toCobolDate(LocalDate date) {
        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}