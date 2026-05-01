package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.jooq.tables.records.AccountRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class DelaccRepository {

    private static final DateTimeFormatter ACCOUNT_AUDIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private final DSLContext dsl;

    public DelaccRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public Optional<AccountDetails> findBySortcodeAndAccountNumberForUpdate(String sortcode, long accountNumber) {
        return dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(accountNumber))
                .forUpdate()
                .fetchOptional(this::toDomain);
    }

    public int deleteAccount(String sortcode, long accountNumber) {
        return dsl.deleteFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(accountNumber))
                .execute();
    }

    public void insertAccountDeletionAudit(
            AccountDetails account,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime
    ) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");

        dsl.insertInto(PROCTRAN)
                .set(PROCTRAN.SORTCODE, account.sortcode())
                .set(PROCTRAN.LOGICAL_DELETE, false)
                .set(PROCTRAN.TRAN_DATE, transactionDate)
                .set(PROCTRAN.TRAN_TIME, transactionTime)
                .set(PROCTRAN.TRAN_REF, transactionReference)
                .set(PROCTRAN.TRAN_TYPE, "ODA")
                .set(PROCTRAN.DESCRIPTION, toAccountDeletionDescription(account))
                .set(PROCTRAN.AMOUNT, account.actualBalance())
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

    private String toAccountDeletionDescription(AccountDetails account) {
        return String.format(
                Locale.ROOT,
                "%010d%-8.8s%s%sDELETE",
                account.customerNumber(),
                account.accountType(),
                toCobolDate(account.lastStatementDate()),
                toCobolDate(account.nextStatementDate())
        );
    }

    private String toCobolDate(LocalDate date) {
        return date == null ? "00000000" : date.format(ACCOUNT_AUDIT_DATE_FORMATTER);
    }
}
