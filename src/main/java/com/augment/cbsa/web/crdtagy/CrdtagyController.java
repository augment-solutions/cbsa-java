package com.augment.cbsa.web.crdtagy;

import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.service.CreditAgencyService;
import com.augment.cbsa.web.crecust.dto.CrecustCommareaResponseDto;
import com.augment.cbsa.web.crecust.dto.CrecustRequestDto;
import com.augment.cbsa.web.crecust.dto.CrecustResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/crdtagy")
public class CrdtagyController {

    private static final long CREDIT_AGENCY_TIMEOUT_SECONDS = 5;

    private final CreditAgencyService creditAgencyService;

    public CrdtagyController(CreditAgencyService creditAgencyService) {
        this.creditAgencyService = Objects.requireNonNull(creditAgencyService, "creditAgencyService must not be null");
    }

    @PostMapping("/{agencyNumber}")
    public CrecustResponseDto process(
            @PathVariable @Min(1) @Max(5) int agencyNumber,
            @Valid @RequestBody CrecustRequestDto requestDto
    ) {
        var commarea = requestDto.creCust();
        int creditScore = awaitCreditScore(new CrecustRequest(
                commarea.commName(),
                commarea.commAddress(),
                commarea.commDateOfBirth()
        ), agencyNumber);

        // Mirror the convention used by the other Java endpoint controllers
        // (e.g. DBCRFUN, DELACC): on the success path we set CommSuccess="Y"
        // and CommFailCode="0" rather than echoing whatever placeholder the
        // caller sent, so the HTTP response carries terminal indicators that
        // line up with the populated CommCreditScore. CRDTAGY1 itself never
        // touches these flags in COBOL — the parent CRECUST orchestrator
        // does — but at the Java endpoint level they are the natural
        // success/fail flag for the per-agency call.
        return new CrecustResponseDto(new CrecustCommareaResponseDto(
                defaultString(commarea.commEyecatcher()),
                commarea.commKey(),
                commarea.commName(),
                commarea.commAddress(),
                commarea.commDateOfBirth(),
                creditScore,
                defaultInt(commarea.commCsReviewDate()),
                "Y",
                "0"
        ));
    }

    private int awaitCreditScore(CrecustRequest request, int agencyNumber) {
        CompletableFuture<java.util.Optional<Integer>> future =
                creditAgencyService.requestCreditScore(request, agencyNumber);
        try {
            return future.get(CREDIT_AGENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS).orElse(0);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new CbsaAbendException("PLOP", "Credit agency processing timed out.", exception);
        } catch (ExecutionException | CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Credit agency processing failed.", cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new CbsaAbendException("PLOP", "Credit agency processing was interrupted.", exception);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}