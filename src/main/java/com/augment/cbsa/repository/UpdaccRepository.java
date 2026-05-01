package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.UpdaccRequest;
import com.augment.cbsa.domain.UpdaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.jooq.tables.records.AccountRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;

@Repository
public class UpdaccRepository {

    private static final String READ_ABEND_CODE = "HRAC";
    private static final String UPDATE_ABEND_CODE = "HUAC";

    private final DSLContext dsl;

    public UpdaccRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public UpdaccResult updateAccount(String sortcode, UpdaccRequest request) {
        Objects.requireNonNull(sortcode, "sortcode must not be null");
        Objects.requireNonNull(request, "request must not be null");

        try {
            return CrdbRetry.run(
                    dsl,
                    () -> dsl.transactionResult(configuration -> updateAccount(DSL.using(configuration), sortcode, request))
            );
        } catch (CbsaAbendException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(UPDATE_ABEND_CODE, "UPDACC failed to persist the account data.", exception);
        }
    }

    private UpdaccResult updateAccount(DSLContext txDsl, String sortcode, UpdaccRequest request) {
        AccountRecord existingRecord;
        try {
            existingRecord = txDsl.selectFrom(ACCOUNT)
                    .where(ACCOUNT.SORTCODE.eq(sortcode))
                    .and(ACCOUNT.ACCOUNT_NUMBER.eq(request.accountNumber()))
                    .forUpdate()
                    .fetchOne();
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(READ_ABEND_CODE, "UPDACC failed to read the account data.", exception);
        }

        if (existingRecord == null) {
            return UpdaccResult.failure("1", "Account number %d was not found.".formatted(request.accountNumber()));
        }

        BigDecimal interestRate = request.interestRate().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal overdraftLimit = BigDecimal.valueOf(request.overdraftLimit()).setScale(2, RoundingMode.HALF_EVEN);

        try {
            int updatedRows = txDsl.update(ACCOUNT)
                    .set(ACCOUNT.ACCOUNT_TYPE, request.accountType())
                    .set(ACCOUNT.INTEREST_RATE, interestRate)
                    .set(ACCOUNT.OVERDRAFT_LIMIT, overdraftLimit)
                    .where(ACCOUNT.SORTCODE.eq(sortcode))
                    .and(ACCOUNT.ACCOUNT_NUMBER.eq(request.accountNumber()))
                    .execute();
            if (updatedRows != 1) {
                throw new CbsaAbendException(UPDATE_ABEND_CODE, "UPDACC failed to update the account data.");
            }
        } catch (DataAccessException exception) {
            if (isSerializationFailure(exception)) {
                throw exception;
            }
            throw new CbsaAbendException(UPDATE_ABEND_CODE, "UPDACC failed to update the account data.", exception);
        }

        return UpdaccResult.success(new AccountDetails(
                existingRecord.getSortcode(),
                existingRecord.getCustomerNumber(),
                existingRecord.getAccountNumber(),
                request.accountType(),
                interestRate,
                existingRecord.getOpened(),
                overdraftLimit,
                existingRecord.getLastStmtDate(),
                existingRecord.getNextStmtDate(),
                existingRecord.getAvailableBalance(),
                existingRecord.getActualBalance()
        ));
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