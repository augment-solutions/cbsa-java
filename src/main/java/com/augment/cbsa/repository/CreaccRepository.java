package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CreaccCommand;
import com.augment.cbsa.domain.CreaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CONTROL;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class CreaccRepository {

    private static final String GLOBAL_CONTROL_ID = "GLOBAL";
    private static final String COUNTER_ABEND_CODE = "HNCS";
    private static final String PROCTRAN_ABEND_CODE = "HWPT";
    private static final long MAX_ACCOUNT_NUMBER = 99_999_999L;
    private static final DateTimeFormatter COBOL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final DSLContext dsl;

    public CreaccRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public CreaccResult createAccount(CreaccCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        try {
            return CrdbRetry.run(dsl, () -> dsl.transactionResult(configuration -> createAccount(DSL.using(configuration), command)));
        } catch (RollbackFailureException exception) {
            return exception.result();
        } catch (CbsaAbendException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC failed to persist the account data.", exception);
        }
    }

    private CreaccResult createAccount(DSLContext txDsl, CreaccCommand command) {
        Record2<Long, Long> controlState;
        try {
            controlState = txDsl.select(CONTROL.ACCOUNT_COUNT, CONTROL.ACCOUNT_LAST)
                    .from(CONTROL)
                    .where(CONTROL.ID.eq(GLOBAL_CONTROL_ID))
                    .forUpdate()
                    .fetchOne();
        } catch (DataAccessException exception) {
            // Re-throw serialization failures so CrdbRetry can retry the
            // whole transaction; only abend on non-retryable errors.
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC failed to reserve the next account number.", exception);
        }

        if (controlState == null) {
            throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC account control record is missing.");
        }

        long nextAccountNumber = safeIncrement(controlState.value2());
        long nextAccountCount = safeIncrement(controlState.value1());
        AccountDetails account = new AccountDetails(
                command.sortcode(),
                command.customerNumber(),
                nextAccountNumber,
                command.accountType(),
                command.interestRate(),
                command.opened(),
                command.overdraftLimit(),
                command.lastStatementDate(),
                command.nextStatementDate(),
                command.availableBalance(),
                command.actualBalance()
        );

        try {
            int updated = txDsl.update(CONTROL)
                    .set(CONTROL.ACCOUNT_COUNT, nextAccountCount)
                    .set(CONTROL.ACCOUNT_LAST, nextAccountNumber)
                    .where(CONTROL.ID.eq(GLOBAL_CONTROL_ID))
                    .and(CONTROL.ACCOUNT_COUNT.eq(controlState.value1()))
                    .and(CONTROL.ACCOUNT_LAST.eq(controlState.value2()))
                    .execute();
            if (updated != 1) {
                throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC failed to update the account counter.");
            }
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC failed to update the account counter.", exception);
        }

        try {
            txDsl.insertInto(ACCOUNT)
                    .set(ACCOUNT.SORTCODE, account.sortcode())
                    .set(ACCOUNT.ACCOUNT_NUMBER, account.accountNumber())
                    .set(ACCOUNT.CUSTOMER_NUMBER, account.customerNumber())
                    .set(ACCOUNT.ACCOUNT_TYPE, account.accountType())
                    .set(ACCOUNT.INTEREST_RATE, account.interestRate())
                    .set(ACCOUNT.OPENED, account.opened())
                    .set(ACCOUNT.OVERDRAFT_LIMIT, account.overdraftLimit())
                    .set(ACCOUNT.LAST_STMT_DATE, account.lastStatementDate())
                    .set(ACCOUNT.NEXT_STMT_DATE, account.nextStatementDate())
                    .set(ACCOUNT.AVAILABLE_BALANCE, account.availableBalance())
                    .set(ACCOUNT.ACTUAL_BALANCE, account.actualBalance())
                    .execute();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw rollbackFailure("7", "Unable to create the account record.");
        }

        try {
            txDsl.insertInto(PROCTRAN)
                    .set(PROCTRAN.SORTCODE, account.sortcode())
                    .set(PROCTRAN.LOGICAL_DELETE, false)
                    .set(PROCTRAN.TRAN_DATE, command.transactionDate())
                    .set(PROCTRAN.TRAN_TIME, command.transactionTime())
                    .set(PROCTRAN.TRAN_REF, command.transactionReference())
                    .set(PROCTRAN.TRAN_TYPE, "OCA")
                    .set(PROCTRAN.DESCRIPTION, toDescription(account))
                    .set(PROCTRAN.AMOUNT, new BigDecimal("0.00"))
                    .execute();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(PROCTRAN_ABEND_CODE, "CREACC failed to write the audit trail.", exception);
        }

        return CreaccResult.success(account);
    }

    private static boolean isSerializationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "40001".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long safeIncrement(long currentValue) {
        long nextValue;
        try {
            nextValue = Math.addExact(currentValue, 1L);
        } catch (ArithmeticException exception) {
            throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC account numbering has reached its maximum value.", exception);
        }
        if (nextValue > MAX_ACCOUNT_NUMBER) {
            throw new CbsaAbendException(COUNTER_ABEND_CODE, "CREACC account numbering has reached its maximum value.");
        }
        return nextValue;
    }

    private RollbackFailureException rollbackFailure(String failCode, String message) {
        return new RollbackFailureException(CreaccResult.failure(failCode, message));
    }

    private String toDescription(AccountDetails account) {
        return String.format(
                Locale.ROOT,
                "%010d%-8.8s%s%s      ",
                account.customerNumber(),
                account.accountType(),
                account.lastStatementDate().format(COBOL_DATE_FORMATTER),
                account.nextStatementDate().format(COBOL_DATE_FORMATTER)
        );
    }

    private static final class RollbackFailureException extends RuntimeException {

        private final CreaccResult result;

        private RollbackFailureException(CreaccResult result) {
            this.result = Objects.requireNonNull(result, "result must not be null");
        }

        private CreaccResult result() {
            return result;
        }
    }
}