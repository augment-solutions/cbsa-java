package com.augment.cbsa.repository;

import com.augment.cbsa.domain.CrecustCommand;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.domain.CustomerDetails;
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

import static com.augment.cbsa.jooq.Tables.CONTROL;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class CrecustRepository {

    private static final String GLOBAL_CONTROL_ID = "GLOBAL";
    private static final DateTimeFormatter PROCTRAN_DOB_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final long MAX_CUSTOMER_NUMBER = 9_999_999_999L;
    // Standard abend code for failures to write the PROCTRAN audit trail.
    // See Section 12 of docs/translation-rules.md.
    private static final String PROCTRAN_ABEND_CODE = "HWPT";
    // Distinct code for serialization-retry exhaustion escaping CrdbRetry, so
    // operationally it is not conflated with a PROCTRAN audit-trail outage.
    private static final String RETRY_EXHAUSTED_ABEND_CODE = "XRTY";

    private final DSLContext dsl;

    public CrecustRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public CrecustResult createCustomer(CrecustCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        try {
            return CrdbRetry.run(dsl, () -> dsl.transactionResult(configuration -> createCustomer(DSL.using(configuration), command)));
        } catch (RollbackFailureException exception) {
            return exception.result();
        } catch (DataAccessException exception) {
            // Inner catches translate every persistence failure into either a
            // domain fail code or PROCTRAN_ABEND_CODE; the only path that
            // escapes CrdbRetry.run is serialization-retry exhaustion.
            if (isSerializationFailure(exception)) {
                throw new CbsaAbendException(
                        RETRY_EXHAUSTED_ABEND_CODE,
                        "CRECUST aborted after exhausting Cockroach serialization retries.",
                        exception
                );
            }
            throw exception;
        }
    }

    private CrecustResult createCustomer(DSLContext txDsl, CrecustCommand command) {
        Record2<Long, Long> controlState;
        try {
            controlState = txDsl.select(CONTROL.CUSTOMER_COUNT, CONTROL.CUSTOMER_LAST)
                    .from(CONTROL)
                    .where(CONTROL.ID.eq(GLOBAL_CONTROL_ID))
                    .forUpdate()
                    .fetchOne();
        } catch (DataAccessException exception) {
            // Re-throw serialization failures so CrdbRetry can retry the
            // transaction; otherwise translate to a domain fail code.
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw rollbackFailure("3", "Unable to reserve the next customer number.");
        }

        if (controlState == null) {
            throw rollbackFailure("4", "Customer control record is missing.");
        }

        long baselineCount = controlState.value1();
        long baselineLast = controlState.value2();
        long nextCustomerNumber;
        long nextCustomerCount;
        try {
            nextCustomerNumber = Math.addExact(baselineLast, 1L);
            nextCustomerCount = Math.addExact(baselineCount, 1L);
        } catch (ArithmeticException exception) {
            throw rollbackFailure("4", "Customer numbering has reached its maximum value.");
        }
        // PROCTRAN.DESCRIPTION carries the customer number formatted as %010d,
        // which is part of the fixed 40-char audit layout. Reject allocations
        // beyond 10 digits up front so we never silently overflow the layout.
        if (nextCustomerNumber > MAX_CUSTOMER_NUMBER) {
            throw rollbackFailure("4", "Customer numbering has reached its maximum value.");
        }

        CustomerDetails customer = new CustomerDetails(
                command.sortcode(),
                nextCustomerNumber,
                command.name(),
                command.address(),
                command.dateOfBirth(),
                command.creditScore(),
                command.reviewDate()
        );

        try {
            txDsl.insertInto(CUSTOMER)
                    .set(CUSTOMER.SORTCODE, customer.sortcode())
                    .set(CUSTOMER.CUSTOMER_NUMBER, customer.customerNumber())
                    .set(CUSTOMER.NAME, customer.name())
                    .set(CUSTOMER.ADDRESS, customer.address())
                    .set(CUSTOMER.DATE_OF_BIRTH, customer.dateOfBirth())
                    .set(CUSTOMER.CREDIT_SCORE, (short) customer.creditScore())
                    .set(CUSTOMER.CS_REVIEW_DATE, customer.csReviewDate())
                    .execute();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw rollbackFailure("1", "Unable to create the customer record.");
        }

        int controlUpdated;
        try {
            controlUpdated = txDsl.update(CONTROL)
                    .set(CONTROL.CUSTOMER_COUNT, nextCustomerCount)
                    .set(CONTROL.CUSTOMER_LAST, nextCustomerNumber)
                    .where(CONTROL.ID.eq(GLOBAL_CONTROL_ID))
                    .and(CONTROL.CUSTOMER_COUNT.eq(baselineCount))
                    .and(CONTROL.CUSTOMER_LAST.eq(baselineLast))
                    .execute();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw rollbackFailure("4", "Unable to update the customer control record.");
        }
        if (controlUpdated != 1) {
            throw rollbackFailure("4", "Unable to update the customer control record.");
        }

        try {
            txDsl.insertInto(PROCTRAN)
                    .set(PROCTRAN.SORTCODE, customer.sortcode())
                    .set(PROCTRAN.LOGICAL_DELETE, false)
                    .set(PROCTRAN.TRAN_DATE, command.transactionDate())
                    .set(PROCTRAN.TRAN_TIME, command.transactionTime())
                    .set(PROCTRAN.TRAN_REF, command.transactionReference())
                    .set(PROCTRAN.TRAN_TYPE, "OCC")
                    .set(PROCTRAN.DESCRIPTION, toDescription(customer))
                    .set(PROCTRAN.AMOUNT, new BigDecimal("0.00"))
                    .execute();
        } catch (DataAccessException exception) {
            // Re-throw serialization failures so CrdbRetry can retry the
            // transaction; only non-retryable PROCTRAN write failures should
            // surface as a system abend.
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(
                    PROCTRAN_ABEND_CODE,
                    "CRECUST failed to write the audit trail.",
                    exception
            );
        }

        return CrecustResult.success(customer);
    }

    private RollbackFailureException rollbackFailure(String failCode, String message) {
        return new RollbackFailureException(CrecustResult.failure(failCode, message));
    }

    private String toDescription(CustomerDetails customer) {
        return customer.sortcode()
                + String.format("%010d", customer.customerNumber())
                + String.format("%-14.14s", customer.name())
                + customer.dateOfBirth().format(PROCTRAN_DOB_FORMATTER);
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

    private static final class RollbackFailureException extends RuntimeException {

        private final CrecustResult result;

        private RollbackFailureException(CrecustResult result) {
            this.result = Objects.requireNonNull(result, "result must not be null");
        }

        private CrecustResult result() {
            return result;
        }
    }
}
