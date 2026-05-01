package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.jooq.tables.records.AccountRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class XfrfunRepository {

    private static final String TRANSFER_TYPE = "TFR";

    private final DSLContext dsl;

    public XfrfunRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public Optional<AccountDetails> findBySortcodeAndAccountNumberForUpdate(String sortcode, long accountNumber) {
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

    public void insertTransferAudit(
            String fromSortcode,
            long fromAccountNumber,
            String toSortcode,
            long toAccountNumber,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime,
            BigDecimal amount
    ) {
        Objects.requireNonNull(fromSortcode, "fromSortcode must not be null");
        Objects.requireNonNull(toSortcode, "toSortcode must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        dsl.insertInto(PROCTRAN)
                .set(PROCTRAN.SORTCODE, fromSortcode)
                .set(PROCTRAN.LOGICAL_DELETE, false)
                .set(PROCTRAN.TRAN_DATE, transactionDate)
                .set(PROCTRAN.TRAN_TIME, transactionTime)
                .set(PROCTRAN.TRAN_REF, transactionReference)
                .set(PROCTRAN.TRAN_TYPE, TRANSFER_TYPE)
                .set(PROCTRAN.DESCRIPTION, toTransferDescription(toSortcode, toAccountNumber))
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

    private String toTransferDescription(String toSortcode, long toAccountNumber) {
        if (toSortcode.length() != 6) {
            throw new IllegalArgumentException("toSortcode must be exactly 6 characters");
        }
        return String.format(Locale.ROOT, "%-26.26s%s%08d", "TRANSFER", toSortcode, toAccountNumber);
    }
}