package com.augment.cbsa.service;

import com.augment.cbsa.domain.CreditAgency;
import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.error.CbsaAbendException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CreditAgencyService {

    private final CreditAgencyDelayGenerator delayGenerator;
    private final CreditAgencyDelayExecutor delayExecutor;
    private final CreditAgencyScoreGenerator scoreGenerator;

    public CreditAgencyService(
            CreditAgencyDelayGenerator delayGenerator,
            CreditAgencyDelayExecutor delayExecutor,
            CreditAgencyScoreGenerator scoreGenerator
    ) {
        this.delayGenerator = Objects.requireNonNull(delayGenerator, "delayGenerator must not be null");
        this.delayExecutor = Objects.requireNonNull(delayExecutor, "delayExecutor must not be null");
        this.scoreGenerator = Objects.requireNonNull(scoreGenerator, "scoreGenerator must not be null");
    }

    @Async("creditAgencyExecutor")
    public CompletableFuture<Optional<Integer>> requestCreditScore(CrecustRequest request, int agencyNumber) {
        Objects.requireNonNull(request, "request must not be null");
        CreditAgency agency = CreditAgency.fromAgencyNumber(agencyNumber);

        try {
            delayExecutor.delay(delayGenerator.nextDelay(agency));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(new CbsaAbendException(
                    "PLOP",
                    "Credit agency processing was interrupted.",
                    exception
            ));
        }

        int score = scoreGenerator.nextCreditScore(agency, request);
        if (score < 1 || score > 999) {
            throw new IllegalArgumentException("credit agency score must be between 1 and 999");
        }

        return CompletableFuture.completedFuture(Optional.of(score));
    }
}
