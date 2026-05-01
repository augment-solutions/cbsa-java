package com.augment.cbsa.web.xfrfun;

import com.augment.cbsa.domain.XfrfunRequest;
import com.augment.cbsa.domain.XfrfunResult;
import com.augment.cbsa.service.XfrfunService;
import com.augment.cbsa.web.xfrfun.dto.XfrfunCommareaRequestDto;
import com.augment.cbsa.web.xfrfun.dto.XfrfunCommareaResponseDto;
import com.augment.cbsa.web.xfrfun.dto.XfrfunRequestDto;
import com.augment.cbsa.web.xfrfun.dto.XfrfunResponseDto;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/v1/xfrfun")
public class XfrfunController {

    private final XfrfunService xfrfunService;
    private final String sortcode;

    public XfrfunController(XfrfunService xfrfunService, @Value("${cbsa.sortcode}") String sortcode) {
        this.xfrfunService = Objects.requireNonNull(xfrfunService, "xfrfunService must not be null");
        this.sortcode = Objects.requireNonNull(sortcode, "sortcode must not be null");
    }

    @PostMapping
    public ResponseEntity<?> transfer(@Valid @RequestBody XfrfunRequestDto requestDto) {
        XfrfunCommareaRequestDto commarea = requestDto.xfrfun();
        XfrfunRequest serviceRequest = new XfrfunRequest(
                commarea.commFaccno(),
                commarea.commTaccno(),
                commarea.commAmt()
        );
        XfrfunResult result = xfrfunService.transfer(serviceRequest);

        if (!result.transferSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(new XfrfunResponseDto(new XfrfunCommareaResponseDto(
                commarea.commFaccno(),
                Integer.parseInt(sortcode),
                commarea.commTaccno(),
                Integer.parseInt(sortcode),
                serviceRequest.amount(),
                result.fromAvailableBalance(),
                result.fromActualBalance(),
                result.toAvailableBalance(),
                result.toActualBalance(),
                "0",
                "Y"
        )));
    }

    private HttpStatus failureStatus(XfrfunResult result) {
        if (result.isFromAccountNotFoundFailure() || result.isToAccountNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isInvalidAmountFailure()) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(XfrfunResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(XfrfunResult result) {
        if (result.isFromAccountNotFoundFailure()) {
            return "From account not found";
        }
        if (result.isToAccountNotFoundFailure()) {
            return "To account not found";
        }
        if (result.isInvalidAmountFailure()) {
            return "Invalid transfer amount";
        }
        return "Transfer failed";
    }
}