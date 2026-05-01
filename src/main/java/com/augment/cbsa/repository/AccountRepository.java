package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.jooq.tables.records.AccountRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;

@Repository
public class AccountRepository {

    private final DSLContext dsl;

    public AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<AccountDetails> findBySortcodeAndAccountNumber(String sortcode, long accountNumber) {
        return dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(accountNumber))
                .fetchOptional(this::toDomain);
    }

    public Optional<AccountDetails> findLastBySortcode(String sortcode) {
        return dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .orderBy(ACCOUNT.ACCOUNT_NUMBER.desc())
                .limit(1)
                .fetchOptional(this::toDomain);
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