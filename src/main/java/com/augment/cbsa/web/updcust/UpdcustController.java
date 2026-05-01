package com.augment.cbsa.web.updcust;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.service.UpdcustService;
import com.augment.cbsa.web.updcust.dto.UpdcustCommareaResponseDto;
import com.augment.cbsa.web.updcust.dto.UpdcustRequestDto;
import com.augment.cbsa.web.updcust.dto.UpdcustResponseDto;
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
@RequestMapping("/api/v1/updcust")
public class UpdcustController {

    private static final String EYE_CATCHER = "CUST";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final UpdcustService updcustService;

    public UpdcustController(UpdcustService updcustService) {
        this.updcustService = Objects.requireNonNull(updcustService, "updcustService must not be null");
    }

    @PutMapping("/update")
    public ResponseEntity<?> update(@Valid @RequestBody UpdcustRequestDto requestDto) {
        // COBOL overwrites COMM-SCODE with the branch SORTCODE constant before any
        // datastore access, so the Java translation ignores the incoming value too.
        UpdcustRequest request = new UpdcustRequest(
                Long.parseLong(requestDto.updCust().commCustno()),
                requestDto.updCust().commName(),
                requestDto.updCust().commAddress(),
                requestDto.updCust().commDob(),
                requestDto.updCust().commCreditScore(),
                requestDto.updCust().commCsReviewDate()
        );
        UpdcustResult result = updcustService.update(request);

        if (!result.updateSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(UpdcustResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isValidationFailure()) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(UpdcustResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(UpdcustResult result) {
        return switch (result.failCode()) {
            case "1" -> "Customer not found";
            case "4" -> "Customer name and address required";
            case "T" -> "Invalid customer title";
            case "2" -> "Customer datastore read failed";
            default -> "Customer update failed";
        };
    }

    private UpdcustResponseDto toResponse(UpdcustResult result) {
        CustomerDetails customer = Objects.requireNonNull(result.customer(), "Successful response requires a customer");
        return new UpdcustResponseDto(new UpdcustCommareaResponseDto(
                EYE_CATCHER,
                customer.sortcode(),
                String.format(Locale.ROOT, "%010d", customer.customerNumber()),
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
        if (date == null) {
            return 0;
        }
        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}
