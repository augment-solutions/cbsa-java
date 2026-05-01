package com.augment.cbsa.service;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.XfrfunRequest;
import com.augment.cbsa.domain.XfrfunResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.CrdbRetry;
import com.augment.cbsa.repository.XfrfunRepository;
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
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class XfrfunService {

    private static final String FROM_ACCOUNT_NOT_FOUND_CODE = "1";
    private static final String TO_ACCOUNT_NOT_FOUND_CODE = "2";
    private static final String INVALID_AMOUNT_CODE = "4";
    private static final String SAME_ACCOUNT_ABEND_CODE = "SAME";
    private static final String FROM_ACCOUNT_ABEND_CODE = "FROM";
    private static final String TO_ACCOUNT_ABEND_CODE = "TO  ";
    // Standard abend code for failures to write the PROCTRAN audit trail.
    // See Section 12 of docs/translation-rules.md.
    private static final String PROCTRAN_ABEND_CODE = "HWPT";
    private static final String RETRY_EXHAUSTED_ABEND_CODE = "XRTY";

    private final XfrfunRepository xfrfunRepository;
    private final DSLContext dsl;
    private final TransactionTemplate transactionTemplate;
    private final String sortcode;
    private final Clock clock;

    public XfrfunService(
            XfrfunRepository xfrfunRepository,
            DSLContext dsl,
            TransactionTemplate transactionTemplate,
            CbsaProperties cbsaProperties,
            Clock clock
    ) {
        this.xfrfunRepository = Objects.requireNonNull(xfrfunRepository, "xfrfunRepository must not be null");
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.sortcode = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null").sortcode();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public XfrfunResult transfer(XfrfunRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.amount().signum() <= 0) {
            return XfrfunResult.failure(INVALID_AMOUNT_CODE, "Please supply an amount greater than zero.");
        }

        if (request.fromAccountNumber() == request.toAccountNumber()) {
            throw new CbsaAbendException(SAME_ACCOUNT_ABEND_CODE, "XFRFUN cannot transfer funds to the same account.");
        }

        Instant now = Instant.now(clock);
        try {
            return Objects.requireNonNull(
                    CrdbRetry.run(dsl, () -> transactionTemplate.execute(status -> transferWithinTransaction(request, now))),
                    "transactionTemplate returned null result"
            );
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw new CbsaAbendException(
                        RETRY_EXHAUSTED_ABEND_CODE,
                        "XFRFUN aborted after exhausting Cockroach serialization retries.",
                        exception
                );
            }
            throw exception;
        }
    }

    private XfrfunResult transferWithinTransaction(XfrfunRequest request, Instant now) {
        OrderedAccounts orderedAccounts = lockAccountsInDeterministicOrder(request);
        if (orderedAccounts.failure() != null) {
            return orderedAccounts.failure();
        }

        AccountDetails fromAccount = Objects.requireNonNull(orderedAccounts.fromAccount(), "fromAccount must not be null");
        AccountDetails toAccount = Objects.requireNonNull(orderedAccounts.toAccount(), "toAccount must not be null");

        BigDecimal updatedFromAvailableBalance = scale(fromAccount.availableBalance().subtract(request.amount()));
        BigDecimal updatedFromActualBalance = scale(fromAccount.actualBalance().subtract(request.amount()));
        BigDecimal updatedToAvailableBalance = scale(toAccount.availableBalance().add(request.amount()));
        BigDecimal updatedToActualBalance = scale(toAccount.actualBalance().add(request.amount()));

        updateAccountOrThrow(FROM_ACCOUNT_ABEND_CODE, "FROM", fromAccount.accountNumber(), updatedFromAvailableBalance, updatedFromActualBalance);
        updateAccountOrThrow(TO_ACCOUNT_ABEND_CODE, "TO", toAccount.accountNumber(), updatedToAvailableBalance, updatedToActualBalance);
        insertAuditOrThrow(request, now);

        return XfrfunResult.success(
                updatedFromAvailableBalance,
                updatedFromActualBalance,
                updatedToAvailableBalance,
                updatedToActualBalance
        );
    }

    private OrderedAccounts lockAccountsInDeterministicOrder(XfrfunRequest request) {
        if (request.fromAccountNumber() < request.toAccountNumber()) {
            Optional<AccountDetails> fromAccount = findAccountForUpdate(FROM_ACCOUNT_ABEND_CODE, "FROM", request.fromAccountNumber());
            if (fromAccount.isEmpty()) {
                return OrderedAccounts.failure(XfrfunResult.failure(
                        FROM_ACCOUNT_NOT_FOUND_CODE,
                        "From account number %d was not found.".formatted(request.fromAccountNumber())
                ));
            }

            Optional<AccountDetails> toAccount = findAccountForUpdate(TO_ACCOUNT_ABEND_CODE, "TO", request.toAccountNumber());
            if (toAccount.isEmpty()) {
                return OrderedAccounts.failure(XfrfunResult.failure(
                        TO_ACCOUNT_NOT_FOUND_CODE,
                        "To account number %d was not found.".formatted(request.toAccountNumber())
                ));
            }

            return OrderedAccounts.success(fromAccount.get(), toAccount.get());
        }

        // fromAccountNumber > toAccountNumber: lock TO first to preserve a consistent
        // global lock order, but mirror COBOL's failure precedence (FROM-not-found wins
        // over TO-not-found) by probing the FROM row when the TO row is missing.
        Optional<AccountDetails> toAccount = findAccountForUpdate(TO_ACCOUNT_ABEND_CODE, "TO", request.toAccountNumber());
        if (toAccount.isEmpty()) {
            Optional<AccountDetails> fromProbe = findAccountForUpdate(FROM_ACCOUNT_ABEND_CODE, "FROM", request.fromAccountNumber());
            if (fromProbe.isEmpty()) {
                return OrderedAccounts.failure(XfrfunResult.failure(
                        FROM_ACCOUNT_NOT_FOUND_CODE,
                        "From account number %d was not found.".formatted(request.fromAccountNumber())
                ));
            }
            return OrderedAccounts.failure(XfrfunResult.failure(
                    TO_ACCOUNT_NOT_FOUND_CODE,
                    "To account number %d was not found.".formatted(request.toAccountNumber())
            ));
        }

        Optional<AccountDetails> fromAccount = findAccountForUpdate(FROM_ACCOUNT_ABEND_CODE, "FROM", request.fromAccountNumber());
        if (fromAccount.isEmpty()) {
            return OrderedAccounts.failure(XfrfunResult.failure(
                    FROM_ACCOUNT_NOT_FOUND_CODE,
                    "From account number %d was not found.".formatted(request.fromAccountNumber())
            ));
        }

        return OrderedAccounts.success(fromAccount.get(), toAccount.get());
    }

    private Optional<AccountDetails> findAccountForUpdate(String abendCode, String accountRole, long accountNumber) {
        try {
            return xfrfunRepository.findBySortcodeAndAccountNumberForUpdate(sortcode, accountNumber);
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(
                    abendCode,
                    "XFRFUN failed while reading the %s account %d.".formatted(accountRole, accountNumber),
                    exception
            );
        }
    }

    private void updateAccountOrThrow(
            String abendCode,
            String accountRole,
            long accountNumber,
            BigDecimal availableBalance,
            BigDecimal actualBalance
    ) {
        try {
            int updatedRows = xfrfunRepository.updateBalances(sortcode, accountNumber, availableBalance, actualBalance);
            if (updatedRows != 1) {
                throw new CbsaAbendException(
                        abendCode,
                        "XFRFUN unexpectedly updated %d %s account rows for account %d."
                                .formatted(updatedRows, accountRole, accountNumber)
                );
            }
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(
                    abendCode,
                    "XFRFUN failed while updating the %s account %d.".formatted(accountRole, accountNumber),
                    exception
            );
        }
    }

    private void insertAuditOrThrow(XfrfunRequest request, Instant now) {
        try {
            xfrfunRepository.insertTransferAudit(
                    sortcode,
                    request.fromAccountNumber(),
                    sortcode,
                    request.toAccountNumber(),
                    Math.max(0L, now.toEpochMilli()),
                    LocalDate.ofInstant(now, ZoneOffset.UTC),
                    LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
                    request.amount()
            );
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(PROCTRAN_ABEND_CODE, "XFRFUN failed to write the transfer audit trail.", exception);
        }
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

    private record OrderedAccounts(AccountDetails fromAccount, AccountDetails toAccount, XfrfunResult failure) {

        private static OrderedAccounts success(AccountDetails fromAccount, AccountDetails toAccount) {
            return new OrderedAccounts(fromAccount, toAccount, null);
        }

        private static OrderedAccounts failure(XfrfunResult failure) {
            return new OrderedAccounts(null, null, failure);
        }
    }
}