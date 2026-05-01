package com.augment.cbsa.service;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DelaccRequest;
import com.augment.cbsa.domain.DelaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.CrdbRetry;
import com.augment.cbsa.repository.DelaccRepository;
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
public class DelaccService {

    private static final String NOT_FOUND_CODE = "1";
    private static final String DELETE_FAILURE_CODE = "3";
    private static final String READ_ABEND_CODE = "HRAC";
    private static final String PROCTRAN_ABEND_CODE = "HWPT";

    private final DelaccRepository delaccRepository;
    private final DSLContext dsl;
    private final TransactionTemplate transactionTemplate;
    private final String sortcode;
    private final Clock clock;

    public DelaccService(
            DelaccRepository delaccRepository,
            DSLContext dsl,
            TransactionTemplate transactionTemplate,
            CbsaProperties cbsaProperties,
            Clock clock
    ) {
        this.delaccRepository = Objects.requireNonNull(delaccRepository, "delaccRepository must not be null");
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.sortcode = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null").sortcode();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DelaccResult delete(DelaccRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return Objects.requireNonNull(
                CrdbRetry.run(dsl, () -> transactionTemplate.execute(status -> deleteWithinTransaction(request))),
                "transactionTemplate returned null result"
        );
    }

    private DelaccResult deleteWithinTransaction(DelaccRequest request) {
        Optional<AccountDetails> account;
        try {
            account = delaccRepository.findBySortcodeAndAccountNumberForUpdate(sortcode, request.accountNumber());
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(READ_ABEND_CODE, "DELACC failed to read the account data.", exception);
        }

        if (account.isEmpty()) {
            return DelaccResult.failure(
                    NOT_FOUND_CODE,
                    request.accountNumber(),
                    "Account number %d was not found.".formatted(request.accountNumber())
            );
        }

        AccountDetails accountDetails = account.get();

        try {
            int deletedRows = delaccRepository.deleteAccount(sortcode, request.accountNumber());
            if (deletedRows == 0) {
                return deleteFailure(request.accountNumber());
            }
            if (deletedRows > 1) {
                // Unique (sortcode, account_number) makes >1 impossible; throw to roll back
                // any unexpected over-delete and fail loudly instead of committing silently.
                throw new CbsaAbendException(PROCTRAN_ABEND_CODE,
                        "DELACC unexpectedly deleted %d rows for account %d.".formatted(deletedRows, request.accountNumber()));
            }
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            return deleteFailure(request.accountNumber());
        }

        Instant now = Instant.now(clock);

        try {
            delaccRepository.insertAccountDeletionAudit(
                    accountDetails,
                    Math.max(0L, now.toEpochMilli()),
                    LocalDate.ofInstant(now, ZoneOffset.UTC),
                    LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
            );
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(PROCTRAN_ABEND_CODE, "DELACC failed to write the account deletion audit trail.", exception);
        }

        return DelaccResult.success(accountDetails);
    }

    private DelaccResult deleteFailure(long accountNumber) {
        return DelaccResult.failure(
                DELETE_FAILURE_CODE,
                accountNumber,
                "Account number %d could not be deleted.".formatted(accountNumber)
        );
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
