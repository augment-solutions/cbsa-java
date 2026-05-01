package com.augment.cbsa.web.crecust;

import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.service.CrecustService;
import com.augment.cbsa.web.crecust.dto.CrecustCommareaResponseDto;
import com.augment.cbsa.web.crecust.dto.CrecustKeyDto;
import com.augment.cbsa.web.crecust.dto.CrecustRequestDto;
import com.augment.cbsa.web.crecust.dto.CrecustResponseDto;
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
@RequestMapping("/api/v1/crecust")
public class CrecustController {

    private static final String EYE_CATCHER = "CUST";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final CrecustService crecustService;

    public CrecustController(CrecustService crecustService) {
        this.crecustService = Objects.requireNonNull(crecustService, "crecustService must not be null");
    }

    @PostMapping("/insert")
    public ResponseEntity<?> create(@Valid @RequestBody CrecustRequestDto requestDto) {
        CrecustRequest request = new CrecustRequest(
                requestDto.creCust().commName(),
                requestDto.creCust().commAddress(),
                requestDto.creCust().commDateOfBirth()
        );
        CrecustResult result = crecustService.create(request);

        if (!result.creationSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(CrecustResult result) {
        if (result.isValidationFailure()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (result.isCreditFailure()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(CrecustResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(CrecustResult result) {
        return switch (result.failCode()) {
            case "T" -> "Invalid customer title";
            case "O", "Y", "Z" -> "Invalid date of birth";
            case "A", "B", "C", "D", "G" -> "Credit check unavailable";
            default -> "Customer creation failed";
        };
    }

    private CrecustResponseDto toResponse(CrecustResult result) {
        CustomerDetails customer = Objects.requireNonNull(result.customer(), "Successful response requires a customer");
        return new CrecustResponseDto(new CrecustCommareaResponseDto(
                EYE_CATCHER,
                new CrecustKeyDto(customer.sortcode(), customer.customerNumber()),
                customer.name(),
                customer.address(),
                toCobolDate(customer.dateOfBirth()),
                customer.creditScore(),
                toCobolDate(customer.csReviewDate()),
                "Y",
                ""
        ));
    }

    private int toCobolDate(LocalDate date) {
        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}