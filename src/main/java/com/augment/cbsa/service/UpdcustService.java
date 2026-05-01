package com.augment.cbsa.service;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.repository.UpdcustRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UpdcustService {

    private static final List<String> VALID_TITLES = List.of(
            "Professor", "Mr", "Mrs", "Miss", "Ms", "Dr", "Drs", "Lord", "Sir", "Lady", ""
    );

    private final UpdcustRepository updcustRepository;
    private final String sortcode;
    private final Clock clock;

    public UpdcustService(UpdcustRepository updcustRepository, CbsaProperties cbsaProperties, Clock clock) {
        this.updcustRepository = Objects.requireNonNull(updcustRepository, "updcustRepository must not be null");
        this.sortcode = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null").sortcode();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public UpdcustResult update(UpdcustRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Optional<UpdcustResult> titleFailure = validateTitle(request.name());
        if (titleFailure.isPresent()) {
            return titleFailure.get();
        }

        Instant now = Instant.now(clock);
        return updcustRepository.updateCustomer(
                sortcode,
                request,
                Math.max(0L, now.toEpochMilli()),
                LocalDate.ofInstant(now, ZoneOffset.UTC),
                LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
        );
    }

    private Optional<UpdcustResult> validateTitle(String name) {
        String firstToken = firstToken(name);
        if (!VALID_TITLES.contains(firstToken)) {
            return Optional.of(UpdcustResult.failure("T", "The customer title is invalid."));
        }
        return Optional.empty();
    }

    private String firstToken(String name) {
        // Mirror COBOL `UNSTRING COMM-NAME DELIMITED BY SPACE INTO WS-UNSTR-TITLE`
        // (UPDCUST.cbl): a leading-space name unstrings to all-spaces, which the
        // COBOL EVALUATE accepts as a valid (blank) title. Do NOT stripLeading
        // here — that would diverge from UPDCUST's title semantics.
        if (name.isEmpty()) {
            return "";
        }
        int delimiterIndex = name.indexOf(' ');
        return delimiterIndex < 0 ? name : name.substring(0, delimiterIndex);
    }
}
