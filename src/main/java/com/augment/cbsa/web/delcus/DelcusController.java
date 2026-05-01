package com.augment.cbsa.web.delcus;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.DelcusRequest;
import com.augment.cbsa.domain.DelcusResult;
import com.augment.cbsa.service.DelcusService;
import com.augment.cbsa.web.delcus.dto.DelcusCommareaResponseDto;
import com.augment.cbsa.web.delcus.dto.DelcusRequestDto;
import com.augment.cbsa.web.delcus.dto.DelcusResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/delcus")
public class DelcusController {

    private static final String EYE_CATCHER = "CUST";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final DelcusService delcusService;

    public DelcusController(DelcusService delcusService) {
        this.delcusService = Objects.requireNonNull(delcusService, "delcusService must not be null");
    }

    @DeleteMapping("/remove/{customerNumber}")
    public ResponseEntity<?> delete(
            @PathVariable
            @Size(max = 10)
            @Pattern(regexp = "0*[1-9][0-9]*")
            String customerNumber,
            @Valid @RequestBody DelcusRequestDto requestDto
    ) {
        // The z/OS Connect contract carries the full commarea shape in the body,
        // but DELCUS itself only consumes COMM-CUSTNO.
        Objects.requireNonNull(requestDto, "requestDto must not be null");

        long pathCustomerNumber = Long.parseLong(customerNumber);
        // Reject body/path mismatches up front. The body's CommCustno is
        // optional (pattern allows ""); when supplied it must agree with the
        // path so we never silently delete the path target on a misaddressed
        // request.
        String bodyCustno = requestDto.delCus().commCustno();
        if (bodyCustno != null && !bodyCustno.isEmpty()
                && Long.parseLong(bodyCustno) != pathCustomerNumber) {
            ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problemDetail.setTitle("Validation failed");
            problemDetail.setDetail("Body CommCustno does not match path customerNumber.");
            return ResponseEntity.badRequest().body(problemDetail);
        }

        DelcusResult result = delcusService.delete(new DelcusRequest(pathCustomerNumber));
        if (!result.deleteSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private HttpStatus failureStatus(DelcusResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isRandomRetryExhaustedFailure()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(DelcusResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(DelcusResult result) {
        if (result.isNotFoundFailure()) {
            return "Customer not found";
        }
        if (result.isRandomRetryExhaustedFailure()) {
            return "Customer deletion retry exhausted";
        }
        return "Customer deletion failed";
    }

    private DelcusResponseDto toResponse(DelcusResult result) {
        CustomerDetails customer = Objects.requireNonNull(result.customer(), "Successful response requires a customer");
        return new DelcusResponseDto(new DelcusCommareaResponseDto(
                EYE_CATCHER,
                customer.sortcode(),
                String.format(Locale.ROOT, "%010d", customer.customerNumber()),
                customer.name(),
                customer.address(),
                toCobolDate(customer.dateOfBirth()),
                customer.creditScore(),
                toCobolDate(customer.csReviewDate()),
                "Y",
                // CommDelFailCd is a fixed-width 1-char commarea slot;
                // emit a single space on success to preserve the length-1
                // contract for clients porting from COBOL.
                " "
        ));
    }

    private int toCobolDate(LocalDate date) {
        if (date == null) {
            return 0;
        }
        return Integer.parseInt(date.format(COBOL_DATE_FORMATTER));
    }
}
