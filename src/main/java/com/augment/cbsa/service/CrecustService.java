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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrecustService {

    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final int CREDIT_AGENCY_COUNT = 5;
    private static final int REVIEW_DATE_BOUND = 20;
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

        Optional<CrecustResult> titleFailure = validateTitle(request.name());
        if (titleFailure.isPresent()) {
            return titleFailure.get();
        }

        Optional<LocalDate> parsedDateOfBirth = parseDateOfBirth(request.dateOfBirth());
        if (parsedDateOfBirth.isEmpty()) {
            return invalidDateFailure(request.dateOfBirth());
        }

        LocalDate dateOfBirth = parsedDateOfBirth.get();
        Optional<CrecustResult> dateFailure = validateDateOfBirth(dateOfBirth);
        if (dateFailure.isPresent()) {
            return dateFailure.get();
        }

        CreditDecision creditDecision = evaluateCredit(request);
        if (creditDecision == null) {
            return CrecustResult.failure("G", "Credit check could not be completed.");
        }

        Instant now = Instant.now(clock);
        CrecustCommand command = new CrecustCommand(
                sortcode,
                request.name(),
                request.address(),
                dateOfBirth,
                creditDecision.creditScore(),
                creditDecision.reviewDate(),
                Math.max(0L, now.toEpochMilli()),
                LocalDate.ofInstant(now, ZoneOffset.UTC),
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
        int year = Integer.parseInt(String.format("%08d", cobolDate).substring(4));
        if (year < 1601) {
            return CrecustResult.failure("O", "Date of birth must not be earlier than 1601.");
        }
        return CrecustResult.failure("Z", "Date of birth is invalid.");
    }

    private Optional<CrecustResult> validateDateOfBirth(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(clock);
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

    private CreditDecision evaluateCredit(CrecustRequest request) {
        List<CompletableFuture<Optional<Integer>>> futures = new ArrayList<>();
        for (int agencyNumber = 1; agencyNumber <= CREDIT_AGENCY_COUNT; agencyNumber++) {
            futures.add(creditAgencyService.requestCreditScore(request, agencyNumber));
        }

        int totalScore = 0;
        int returnedScores = 0;
        for (CompletableFuture<Optional<Integer>> future : futures) {
            try {
                Optional<Integer> maybeScore = future.join();
                if (maybeScore.isPresent()) {
                    totalScore += maybeScore.get();
                    returnedScores++;
                }
            } catch (CompletionException exception) {
                // Ignore individual credit-agency failures and average only successful replies.
            }
        }

        if (returnedScores == 0) {
            return null;
        }

        int reviewOffsetDays = reviewDateRandom.nextInt(REVIEW_DATE_BOUND) + 1;
        return new CreditDecision(totalScore / returnedScores, LocalDate.now(clock).plusDays(reviewOffsetDays));
    }

    private String firstToken(String name) {
        if (name.isEmpty() || Character.isWhitespace(name.charAt(0))) {
            return "";
        }
        int delimiterIndex = name.indexOf(' ');
        return delimiterIndex < 0 ? name : name.substring(0, delimiterIndex);
    }

    private record CreditDecision(int creditScore, LocalDate reviewDate) {
    }
}