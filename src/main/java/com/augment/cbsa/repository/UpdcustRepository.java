package com.augment.cbsa.repository;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.jooq.tables.records.CustomerRecord;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class UpdcustRepository {

    private static final DateTimeFormatter PROCTRAN_DOB_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
    // Legacy COBOL UPDCUST does not write PROCTRAN, but this migration task
    // requires an audit row. Use OUC (branch update customer) and reuse the
    // existing 40-char customer description layout from OCC/ODC.
    private static final String PROCTRAN_UPDATE_CUSTOMER_TYPE = "OUC";
    // Standard abend code for failures to write the PROCTRAN audit trail.
    // Section 12 of docs/translation-rules.md requires every PROCTRAN insert
    // failure to surface as CbsaAbendException("HWPT") rather than a domain
    // fail code so the global handler classifies it as a 500 abend.
    private static final String PROCTRAN_ABEND_CODE = "HWPT";

    private final DSLContext dsl;

    public UpdcustRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public UpdcustResult updateCustomer(
            String sortcode,
            UpdcustRequest request,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime
    ) {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");

        try {
            return CrdbRetry.run(dsl, () -> dsl.transactionResult(configuration -> updateCustomer(
                    DSL.using(configuration),
                    sortcode,
                    request,
                    transactionReference,
                    transactionDate,
                    transactionTime
            )));
        } catch (RollbackFailureException exception) {
            return exception.result();
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(PROCTRAN_ABEND_CODE, "UPDCUST failed to persist the customer data.", exception);
        }
    }

    private UpdcustResult updateCustomer(
            DSLContext txDsl,
            String sortcode,
            UpdcustRequest request,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime
    ) {
        CustomerRecord existingRecord;
        try {
            existingRecord = txDsl.selectFrom(CUSTOMER)
                    .where(CUSTOMER.SORTCODE.eq(sortcode))
                    .and(CUSTOMER.CUSTOMER_NUMBER.eq(request.customerNumber()))
                    .forUpdate()
                    .fetchOne();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw rollbackFailure("2", "Unable to read the customer record.");
        }

        if (existingRecord == null) {
            return UpdcustResult.failure("1", "Customer number %d was not found.".formatted(request.customerNumber()));
        }

        if (isBlankish(request.name()) && isBlankish(request.address())) {
            return UpdcustResult.failure("4", "Customer name and address must not both be blank.");
        }

        String updatedName = existingRecord.getName();
        String updatedAddress = existingRecord.getAddress();

        if (isBlankish(request.name()) && isProvidedForSingleFieldUpdate(request.address())) {
            updatedAddress = request.address();
        }

        if (isBlankish(request.address()) && isProvidedForSingleFieldUpdate(request.name())) {
            updatedName = request.name();
        }

        if (startsWithNonSpace(request.name()) && startsWithNonSpace(request.address())) {
            updatedName = request.name();
            updatedAddress = request.address();
        }

        try {
            int updatedRows = txDsl.update(CUSTOMER)
                    .set(CUSTOMER.NAME, updatedName)
                    .set(CUSTOMER.ADDRESS, updatedAddress)
                    .where(CUSTOMER.SORTCODE.eq(sortcode))
                    .and(CUSTOMER.CUSTOMER_NUMBER.eq(request.customerNumber()))
                    .execute();
            if (updatedRows != 1) {
                throw rollbackFailure("3", "Unable to update the customer record.");
            }
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw rollbackFailure("3", "Unable to update the customer record.");
        }

        CustomerDetails updatedCustomer = toDomain(existingRecord, updatedName, updatedAddress);

        try {
            txDsl.insertInto(PROCTRAN)
                    .set(PROCTRAN.SORTCODE, sortcode)
                    .set(PROCTRAN.LOGICAL_DELETE, false)
                    .set(PROCTRAN.TRAN_DATE, transactionDate)
                    .set(PROCTRAN.TRAN_TIME, transactionTime)
                    .set(PROCTRAN.TRAN_REF, transactionReference)
                    .set(PROCTRAN.TRAN_TYPE, PROCTRAN_UPDATE_CUSTOMER_TYPE)
                    .set(PROCTRAN.DESCRIPTION, toDescription(updatedCustomer))
                    .set(PROCTRAN.AMOUNT, new BigDecimal("0.00"))
                    .execute();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            // PROCTRAN audit-trail failures must surface as a system abend
            // rather than a domain "update failed" fail code so an audit-write
            // outage is not indistinguishable from a CUSTOMER rewrite failure.
            throw new CbsaAbendException(
                    PROCTRAN_ABEND_CODE,
                    "UPDCUST failed to write the audit trail.",
                    exception
            );
        }

        return UpdcustResult.success(updatedCustomer);
    }

    private CustomerDetails toDomain(CustomerRecord record, String updatedName, String updatedAddress) {
        return new CustomerDetails(
                record.getSortcode(),
                record.getCustomerNumber(),
                updatedName,
                updatedAddress,
                record.getDateOfBirth(),
                record.getCreditScore() == null ? 0 : record.getCreditScore(),
                record.getCsReviewDate()
        );
    }

    private String toDescription(CustomerDetails customer) {
        return String.format(
                Locale.ROOT,
                "%s%010d%-14.14s%s",
                customer.sortcode(),
                customer.customerNumber(),
                customer.name(),
                customer.dateOfBirth().format(PROCTRAN_DOB_FORMATTER)
        );
    }

    private boolean isBlankish(String value) {
        return isAllSpaces(value) || startsWithSpace(value);
    }

    private boolean isProvidedForSingleFieldUpdate(String value) {
        return !isBlankish(value);
    }

    private boolean startsWithNonSpace(String value) {
        return !startsWithSpace(value);
    }

    private boolean startsWithSpace(String value) {
        return value == null || value.isEmpty() || value.charAt(0) == ' ';
    }

    private boolean isAllSpaces(String value) {
        return value == null || value.isBlank();
    }

    private RollbackFailureException rollbackFailure(String failCode, String message) {
        return new RollbackFailureException(UpdcustResult.failure(failCode, message));
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

        private final UpdcustResult result;

        private RollbackFailureException(UpdcustResult result) {
            this.result = Objects.requireNonNull(result, "result must not be null");
        }

        private UpdcustResult result() {
            return result;
        }
    }
}
