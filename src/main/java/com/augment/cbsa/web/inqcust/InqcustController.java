package com.augment.cbsa.web.inqcust;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.error.CbsaFailureResponse;
import com.augment.cbsa.service.InqcustService;
import com.augment.cbsa.web.inqcust.dto.InqcustDateDto;
import com.augment.cbsa.web.inqcust.dto.InqcustRequestDto;
import com.augment.cbsa.web.inqcust.dto.InqcustResponseDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/inqcust")
public class InqcustController {

    private static final String EYE_CATCHER = "CUST";

    private final InqcustService inqcustService;

    public InqcustController(InqcustService inqcustService) {
        this.inqcustService = inqcustService;
    }

    @GetMapping("/{customerNumber}")
    public ResponseEntity<?> inquire(
            @PathVariable
            @PositiveOrZero
            @Max(9_999_999_999L)
            long customerNumber
    ) {
        InqcustRequestDto requestDto = new InqcustRequestDto(customerNumber);
        InqcustResult result = inqcustService.inquire(new InqcustRequest(requestDto.customerNumber()));

        if (!result.inquirySuccess()) {
            CbsaFailureResponse failureResponse = new CbsaFailureResponse(result.failCode(), result.message());
            if ("1".equals(result.failCode())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(failureResponse);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failureResponse);
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private InqcustResponseDto toResponse(InqcustResult result) {
        CustomerDetails customer = Objects.requireNonNull(result.customer(), "Successful response requires a customer");
        return new InqcustResponseDto(
                EYE_CATCHER,
                customer.sortcode(),
                customer.customerNumber(),
                customer.name(),
                customer.address(),
                toDate(customer.dateOfBirth()),
                customer.creditScore(),
                toDate(customer.csReviewDate()),
                "Y",
                result.failCode(),
                ""
        );
    }

    private InqcustDateDto toDate(LocalDate date) {
        if (date == null) {
            return InqcustDateDto.zero();
        }

        return new InqcustDateDto(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }
}