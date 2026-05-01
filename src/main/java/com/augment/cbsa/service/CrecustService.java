package com.augment.cbsa.service;

import com.augment.cbsa.domain.CrecustCommand;
import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.repository.CrecustRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrecustService {

    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final int CREDIT_AGENCY_COUNT = 5;
    private static final int REVIEW_DATE_BOUND = 20;
    private static final long CREDIT_AGENCY_TIMEOUT_SECONDS = 6;
    private static final List<String> VALID_TITLES = List.of(
            "Professor", "Mr", "Mrs", "Miss", "Ms", "Dr", "Drs", "Lord", "Sir", "Lady", ""
    );

    private final CrecustRepository crecustRepository;
    private final CreditAgencyService creditAgencyService;
    private final String sortcode;
    private final Clock clock;
    private final Random reviewDateRandom;

    public CrecustService(
            CrecustRepository crecustRepository,
            CreditAgencyService creditAgencyService,
            @Value("${cbsa.sortcode}") String sortcode,
            Clock clock,
            @Qualifier("crecustReviewRandom") Random reviewDateRandom
    ) {
        this.crecustRepository = Objects.requireNonNull(crecustRepository, "crecustRepository must not be null");
        this.creditAgencyService = Objects.requireNonNull(creditAgencyService, "creditAgencyService must not be null");
        this.sortcode = Objects.requireNonNull(sortcode, "sortcode must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.reviewDateRandom = Objects.requireNonNull(reviewDateRandom, "reviewDateRandom must not be null");
    }

    public CrecustResult create(CrecustRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // Capture the clock once so validation, review-date, transaction-date
        // and transaction-time all derive from the same instant. Reading the
        // clock multiple times can otherwise straddle midnight and produce an
        // off-by-one-day audit record.
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Optional<CrecustResult> titleFailure = validateTitle(request.name());
        if (titleFailure.isPresent()) {
            return titleFailure.get();
        }

        Optional<LocalDate> parsedDateOfBirth = parseDateOfBirth(request.dateOfBirth());
        if (parsedDateOfBirth.isEmpty()) {
            return invalidDateFailure(request.dateOfBirth());
        }

        LocalDate dateOfBirth = parsedDateOfBirth.get();
        Optional<CrecustResult> dateFailure = validateDateOfBirth(today, dateOfBirth);
        if (dateFailure.isPresent()) {
            return dateFailure.get();
        }

        CreditDecision creditDecision = evaluateCredit(request, today);
        if (creditDecision == null) {
            return CrecustResult.failure("G", "Credit check could not be completed.");
        }

        CrecustCommand command = new CrecustCommand(
                sortcode,
                request.name(),
                request.address(),
                dateOfBirth,
                creditDecision.creditScore(),
                creditDecision.reviewDate(),
                Math.max(0L, now.toEpochMilli()),
                today,
                LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
        );
        return crecustRepository.createCustomer(command);
    }

    private Optional<CrecustResult> validateTitle(String name) {
        String firstToken = firstToken(name);
        if (!VALID_TITLES.contains(firstToken)) {
            return Optional.of(CrecustResult.failure("T", "The customer title is invalid."));
        }
        return Optional.empty();
    }

    private Optional<LocalDate> parseDateOfBirth(int cobolDate) {
        String normalized = String.format("%08d", cobolDate);
        try {
            return Optional.of(LocalDate.parse(normalized, COBOL_DATE_FORMATTER));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private CrecustResult invalidDateFailure(int cobolDate) {
        // ddMMyyyy: year is the trailing 4 digits. Use modulo on the absolute
        // value so negative or out-of-range inputs cannot throw here and turn a
        // validation issue into a 500.
        int year = Math.abs(cobolDate) % 10000;
        if (year < 1601) {
            return CrecustResult.failure("O", "Date of birth must not be earlier than 1601.");
        }
        return CrecustResult.failure("Z", "Date of birth is invalid.");
    }

    private Optional<CrecustResult> validateDateOfBirth(LocalDate today, LocalDate dateOfBirth) {
        if (dateOfBirth.getYear() < 1601) {
            return Optional.of(CrecustResult.failure("O", "Date of birth must not be earlier than 1601."));
        }
        if (today.getYear() - dateOfBirth.getYear() > 150) {
            return Optional.of(CrecustResult.failure("O", "Date of birth must not be more than 150 years ago."));
        }
        if (dateOfBirth.isAfter(today)) {
            return Optional.of(CrecustResult.failure("Y", "Date of birth must not be in the future."));
        }
        return Optional.empty();
    }

    private CreditDecision evaluateCredit(CrecustRequest request, LocalDate today) {
        List<CompletableFuture<Optional<Integer>>> futures = new ArrayList<>();
        for (int agencyNumber = 1; agencyNumber <= CREDIT_AGENCY_COUNT; agencyNumber++) {
            futures.add(creditAgencyService.requestCreditScore(request, agencyNumber));
        }

        int totalScore = 0;
        int returnedScores = 0;
        boolean interrupted = false;
        for (CompletableFuture<Optional<Integer>> future : futures) {
            if (interrupted) {
                future.cancel(true);
                continue;
            }
            try {
                Optional<Integer> maybeScore = future.get(CREDIT_AGENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (maybeScore.isPresent()) {
                    totalScore += maybeScore.get();
                    returnedScores++;
                }
            } catch (TimeoutException exception) {
                // Bound the wait per agency so a hung credit-agency call cannot
                // block the request indefinitely; treat as fail code G fodder.
                future.cancel(true);
            } catch (ExecutionException | CompletionException exception) {
                // Ignore individual credit-agency failures and average only successful replies.
            } catch (InterruptedException exception) {
                // Treat interruption as an immediate overall credit-check failure
                // so we do not persist a customer based on partially collected
                // scores while cancellation is being signaled.
                Thread.currentThread().interrupt();
                future.cancel(true);
                interrupted = true;
            }
        }

        if (interrupted || returnedScores == 0) {
            return null;
        }

        int reviewOffsetDays = reviewDateRandom.nextInt(REVIEW_DATE_BOUND) + 1;
        return new CreditDecision(totalScore / returnedScores, today.plusDays(reviewOffsetDays));
    }

    private String firstToken(String name) {
        // Strip leading whitespace before extracting the title token so that a
        // leading-whitespace name does not silently bypass title validation by
        // appearing to have an empty (i.e., "no title") prefix.
        String trimmed = name.stripLeading();
        if (trimmed.isEmpty()) {
            return "";
        }
        int delimiterIndex = trimmed.indexOf(' ');
        return delimiterIndex < 0 ? trimmed : trimmed.substring(0, delimiterIndex);
    }

    private record CreditDecision(int creditScore, LocalDate reviewDate) {
    }
}