package com.augment.cbsa.web.dbcrfun;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DbcrfunOrigin;
import com.augment.cbsa.domain.DbcrfunRequest;
import com.augment.cbsa.domain.DbcrfunResult;
import com.augment.cbsa.service.DbcrfunService;
import com.augment.cbsa.web.dbcrfun.dto.DbcrfunCommareaRequestDto;
import com.augment.cbsa.web.dbcrfun.dto.DbcrfunCommareaResponseDto;
import com.augment.cbsa.web.dbcrfun.dto.DbcrfunOriginDto;
import com.augment.cbsa.web.dbcrfun.dto.DbcrfunRequestDto;
import com.augment.cbsa.web.dbcrfun.dto.DbcrfunResponseDto;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/dbcrfun")
public class DbcrfunController {

    private final DbcrfunService dbcrfunService;

    public DbcrfunController(DbcrfunService dbcrfunService) {
        this.dbcrfunService = Objects.requireNonNull(dbcrfunService, "dbcrfunService must not be null");
    }

    @PostMapping
    public ResponseEntity<?> postPayment(@Valid @RequestBody DbcrfunRequestDto requestDto) {
        DbcrfunCommareaRequestDto commarea = requestDto.paydbcr();
        DbcrfunRequest request = new DbcrfunRequest(
                Long.parseLong(commarea.commAccno()),
                commarea.commAmt(),
                toOrigin(commarea.commOrigin())
        );
        DbcrfunResult result = dbcrfunService.process(request);

        if (!result.paymentSuccess()) {
            return ResponseEntity.status(failureStatus(result)).body(failureBody(result));
        }

        return ResponseEntity.ok(toResponse(commarea, request, result));
    }

    private HttpStatus failureStatus(DbcrfunResult result) {
        if (result.isNotFoundFailure()) {
            return HttpStatus.NOT_FOUND;
        }
        if (result.isConflictFailure()) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ProblemDetail failureBody(DbcrfunResult result) {
        HttpStatus status = failureStatus(result);
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(failureTitle(result));
        problemDetail.setDetail(result.message());
        problemDetail.setProperty("failCode", result.failCode());
        return problemDetail;
    }

    private String failureTitle(DbcrfunResult result) {
        if (result.isNotFoundFailure()) {
            return "Account not found";
        }
        if (result.isInsufficientFundsFailure()) {
            return "Insufficient funds";
        }
        if (result.isDisallowedAccountTypeFailure()) {
            return "Payment not permitted";
        }
        return "Payment failed";
    }

    private DbcrfunResponseDto toResponse(DbcrfunCommareaRequestDto commarea, DbcrfunRequest request, DbcrfunResult result) {
        AccountDetails account = Objects.requireNonNull(result.account(), "Successful response requires an account");
        return new DbcrfunResponseDto(new DbcrfunCommareaResponseDto(
                commarea.commAccno(),
                request.amount(),
                account.sortcode(),
                account.availableBalance(),
                account.actualBalance(),
                toDto(request.origin()),
                "Y",
                "0"
        ));
    }

    private DbcrfunOrigin toOrigin(DbcrfunOriginDto originDto) {
        if (originDto == null) {
            return DbcrfunOrigin.blank();
        }
        return new DbcrfunOrigin(
                defaultString(originDto.commApplid()),
                defaultString(originDto.commUserid()),
                defaultString(originDto.commFacilityName()),
                defaultString(originDto.commNetwrkId()),
                originDto.commFaciltype() == null ? 0 : originDto.commFaciltype(),
                defaultString(originDto.fill0())
        );
    }

    private DbcrfunOriginDto toDto(DbcrfunOrigin origin) {
        return new DbcrfunOriginDto(
                origin.applid(),
                origin.userid(),
                origin.facilityName(),
                origin.netwrkId(),
                origin.facilityType(),
                origin.fill0()
        );
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}