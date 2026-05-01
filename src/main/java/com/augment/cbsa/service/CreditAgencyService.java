package com.augment.cbsa.service;

import com.augment.cbsa.domain.CrecustRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CreditAgencyService {

    @Async("creditAgencyExecutor")
    public CompletableFuture<Optional<Integer>> requestCreditScore(CrecustRequest request, int agencyNumber) {
        Objects.requireNonNull(request, "request must not be null");
        if (agencyNumber < 1) {
            throw new IllegalArgumentException("agencyNumber must be positive");
        }

        int score = Math.floorMod(
                Objects.hash(request.name().stripTrailing(), request.address().stripTrailing(), request.dateOfBirth(), agencyNumber),
                900
        ) + 100;
        return CompletableFuture.completedFuture(Optional.of(score));
    }
}
