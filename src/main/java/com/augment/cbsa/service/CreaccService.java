package com.augment.cbsa.service;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.CreaccCommand;
import com.augment.cbsa.domain.CreaccRequest;
import com.augment.cbsa.domain.CreaccResult;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.repository.CreaccRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CreaccService {

    private static final int MAX_ACCOUNTS_PER_CUSTOMER = 9;

    private final CreaccRepository creaccRepository;
    private final InqcustService inqcustService;
    private final InqacccuService inqacccuService;
    private final String sortcode;
    private final Clock clock;

    public CreaccService(
            CreaccRepository creaccRepository,
            InqcustService inqcustService,
            InqacccuService inqacccuService,
            CbsaProperties cbsaProperties,
            Clock clock
    ) {
        this.creaccRepository = Objects.requireNonNull(creaccRepository, "creaccRepository must not be null");
        this.inqcustService = Objects.requireNonNull(inqcustService, "inqcustService must not be null");
        this.inqacccuService = Objects.requireNonNull(inqacccuService, "inqacccuService must not be null");
        this.sortcode = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null").sortcode();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CreaccResult create(CreaccRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (!isSupportedAccountType(request.accountType())) {
            return CreaccResult.failure("A", "Account type must be ISA, MORTGAGE, SAVING, CURRENT, or LOAN.");
        }

        InqcustResult customerResult = inqcustService.inquire(new InqcustRequest(request.customerNumber()));
        if (!customerResult.inquirySuccess()) {
            return CreaccResult.failure("1", customerResult.message());
        }

        InqacccuResult accountCountResult = inqacccuService.inquire(new InqacccuRequest(request.customerNumber()));
        if (!accountCountResult.inquirySuccess()) {
            return CreaccResult.failure("9", accountCountResult.message());
        }
        if (accountCountResult.accounts().size() > MAX_ACCOUNTS_PER_CUSTOMER) {
            return CreaccResult.failure(
                    "8",
                    "Customer number %d already has the maximum number of accounts.".formatted(request.customerNumber())
            );
        }

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        CreaccCommand command = new CreaccCommand(
                sortcode,
                request.customerNumber(),
                request.accountType(),
                request.interestRate(),
                BigDecimal.valueOf(request.overdraftLimit()),
                request.availableBalance(),
                request.actualBalance(),
                today,
                today,
                today.plusDays(30),
                Math.max(0L, now.toEpochMilli()),
                today,
                LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
        );
        return creaccRepository.createAccount(command);
    }

    private boolean isSupportedAccountType(String accountType) {
        if (accountType == null) {
            return false;
        }
        // COBOL ACCOUNT-TYPE-CHECK compared fixed-length prefixes of an 8-byte
        // field (e.g. (1:3)='ISA'). Translate by trimming and matching the
        // exact canonical names so values like "ISAFOO" no longer pass.
        String trimmed = accountType.trim();
        return "ISA".equals(trimmed)
                || "MORTGAGE".equals(trimmed)
                || "SAVING".equals(trimmed)
                || "CURRENT".equals(trimmed)
                || "LOAN".equals(trimmed);
    }
}