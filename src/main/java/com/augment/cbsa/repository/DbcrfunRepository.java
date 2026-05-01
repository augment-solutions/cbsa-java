package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.jooq.tables.records.AccountRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class DbcrfunRepository {

    private final DSLContext dsl;

    public DbcrfunRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public Optional<AccountDetails> lockAccount(String sortcode, long accountNumber) {
        return dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(accountNumber))
                .forUpdate()
                .fetchOptional(this::toDomain);
    }

    public int updateBalances(String sortcode, long accountNumber, BigDecimal availableBalance, BigDecimal actualBalance) {
        return dsl.update(ACCOUNT)
                .set(ACCOUNT.AVAILABLE_BALANCE, availableBalance)
                .set(ACCOUNT.ACTUAL_BALANCE, actualBalance)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(accountNumber))
                .execute();
    }

    public void insertProcTran(
            String sortcode,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime,
            String transactionType,
            String description,
            BigDecimal amount
    ) {
        dsl.insertInto(PROCTRAN)
                .set(PROCTRAN.SORTCODE, sortcode)
                .set(PROCTRAN.LOGICAL_DELETE, false)
                .set(PROCTRAN.TRAN_DATE, transactionDate)
                .set(PROCTRAN.TRAN_TIME, transactionTime)
                .set(PROCTRAN.TRAN_REF, transactionReference)
                .set(PROCTRAN.TRAN_TYPE, transactionType)
                .set(PROCTRAN.DESCRIPTION, description)
                .set(PROCTRAN.AMOUNT, amount)
                .execute();
    }

    private AccountDetails toDomain(AccountRecord record) {
        return new AccountDetails(
                record.getSortcode(),
                record.getCustomerNumber(),
                record.getAccountNumber(),
                record.getAccountType(),
                record.getInterestRate(),
                record.getOpened(),
                record.getOverdraftLimit(),
                record.getLastStmtDate(),
                record.getNextStmtDate(),
                record.getAvailableBalance(),
                record.getActualBalance()
        );
    }
}