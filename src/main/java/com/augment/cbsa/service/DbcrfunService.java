package com.augment.cbsa.service;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DbcrfunOrigin;
import com.augment.cbsa.domain.DbcrfunRequest;
import com.augment.cbsa.domain.DbcrfunResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.CrdbRetry;
import com.augment.cbsa.repository.DbcrfunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DbcrfunService {

    static final int PAYMENT_FACILITY_TYPE = 496;
    // Distinct code for serialization-retry exhaustion escaping CrdbRetry, so
    // operationally it is not conflated with a PROCTRAN audit-trail outage.
    private static final String RETRY_EXHAUSTED_ABEND_CODE = "XRTY";

    private final DbcrfunRepository dbcrfunRepository;
    private final DSLContext dsl;
    private final TransactionTemplate transactionTemplate;
    private final String sortcode;
    private final Clock clock;

    public DbcrfunService(
            DbcrfunRepository dbcrfunRepository,
            DSLContext dsl,
            TransactionTemplate transactionTemplate,
            CbsaProperties cbsaProperties,
            Clock clock
    ) {
        this.dbcrfunRepository = Objects.requireNonNull(dbcrfunRepository, "dbcrfunRepository must not be null");
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.sortcode = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null").sortcode();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DbcrfunResult process(DbcrfunRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Instant now = Instant.now(clock);
        try {
            DbcrfunResult result = CrdbRetry.run(
                    dsl,
                    () -> transactionTemplate.execute(status -> processWithinTransaction(request, now))
            );
            return Objects.requireNonNull(result, "transactionTemplate returned null result");
        } catch (DataAccessException exception) {
            // Inner persistence steps translate every retryable failure into
            // either a domain fail code or PROCTRAN_ABEND_CODE; the only path
            // that escapes CrdbRetry.run is serialization-retry exhaustion.
            // Anything else is an unexpected DAE and is rethrown so the global
            // handler can classify it as UNEX.
            if (isSerializationFailure(exception)) {
                throw new CbsaAbendException(
                        RETRY_EXHAUSTED_ABEND_CODE,
                        "DBCRFUN aborted after exhausting Cockroach serialization retries.",
                        exception
                );
            }
            throw exception;
        }
    }

    private DbcrfunResult processWithinTransaction(DbcrfunRequest request, Instant now) {
        Optional<AccountDetails> found = dbcrfunRepository.lockAccount(sortcode, request.accountNumber());
        if (found.isEmpty()) {
            return DbcrfunResult.failure("1", "Account number %d was not found.".formatted(request.accountNumber()));
        }

        AccountDetails account = found.get();
        if (isPaymentFacility(request.origin()) && isRestrictedAccountType(account.accountType())) {
            return DbcrfunResult.failure(
                    "4",
                    "Payments are not supported for account type %s.".formatted(account.accountType().trim())
            );
        }

        BigDecimal updatedAvailableBalance = scale(account.availableBalance().add(request.amount()));
        if (request.amount().signum() < 0
                && isPaymentFacility(request.origin())
                && updatedAvailableBalance.compareTo(BigDecimal.ZERO) < 0) {
            return DbcrfunResult.failure(
                    "3",
                    "Account number %d does not have sufficient available funds.".formatted(request.accountNumber())
            );
        }

        BigDecimal updatedActualBalance = scale(account.actualBalance().add(request.amount()));
        int updatedRows = dbcrfunRepository.updateBalances(
                sortcode,
                request.accountNumber(),
                updatedAvailableBalance,
                updatedActualBalance
        );
        if (updatedRows != 1) {
            return DbcrfunResult.failure("2", "DBCRFUN failed to update the account record.");
        }

        dbcrfunRepository.insertProcTran(
                sortcode,
                Math.max(0L, now.toEpochMilli()),
                LocalDate.ofInstant(now, ZoneOffset.UTC),
                LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
                transactionType(request),
                description(request),
                request.amount()
        );

        return DbcrfunResult.success(new AccountDetails(
                account.sortcode(),
                account.customerNumber(),
                account.accountNumber(),
                account.accountType(),
                account.interestRate(),
                account.opened(),
                account.overdraftLimit(),
                account.lastStatementDate(),
                account.nextStatementDate(),
                updatedAvailableBalance,
                updatedActualBalance
        ));
    }

    private String transactionType(DbcrfunRequest request) {
        boolean debit = request.amount().signum() < 0;
        if (debit) {
            return isPaymentFacility(request.origin()) ? "PDR" : "DEB";
        }
        return isPaymentFacility(request.origin()) ? "PCR" : "CRE";
    }

    private String description(DbcrfunRequest request) {
        if (isPaymentFacility(request.origin())) {
            return request.origin().paymentDescription();
        }
        return request.amount().signum() < 0 ? "COUNTER WTHDRW" : "COUNTER RECVED";
    }

    private boolean isPaymentFacility(DbcrfunOrigin origin) {
        return origin.facilityType() == PAYMENT_FACILITY_TYPE;
    }

    private boolean isRestrictedAccountType(String accountType) {
        String trimmed = accountType == null ? "" : accountType.trim();
        return "MORTGAGE".equals(trimmed) || "LOAN".equals(trimmed);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static boolean isSerializationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && "40001".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}