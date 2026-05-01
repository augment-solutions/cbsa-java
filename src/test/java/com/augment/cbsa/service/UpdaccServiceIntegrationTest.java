package com.augment.cbsa.service;

import com.augment.cbsa.domain.UpdaccRequest;
import com.augment.cbsa.domain.UpdaccResult;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UpdaccServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UpdaccService updaccService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void updatesOnlyThePermittedAccountFields() {
        insertAccount();

        UpdaccResult result = updaccService.update(new UpdaccRequest(12345678L, "MORTGAGE", new BigDecimal("2.25"), 500L));

        assertThat(result.updateSuccess()).isTrue();
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountType()).isEqualTo("MORTGAGE");
        assertThat(result.account().interestRate()).isEqualTo(new BigDecimal("2.25"));
        assertThat(result.account().overdraftLimit()).isEqualTo(new BigDecimal("500.00"));
        assertThat(result.account().opened()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(result.account().lastStatementDate()).isEqualTo(LocalDate.of(2024, 2, 3));
        assertThat(result.account().nextStatementDate()).isEqualTo(LocalDate.of(2024, 3, 4));
        assertThat(result.account().availableBalance()).isEqualTo(new BigDecimal("1500.25"));
        assertThat(result.account().actualBalance()).isEqualTo(new BigDecimal("1499.75"));

        assertThat(dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(12345678L))
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record).isNotNull();
                    assertThat(record.getAccountType()).isEqualTo("MORTGAGE");
                    assertThat(record.getInterestRate()).isEqualTo(new BigDecimal("2.25"));
                    assertThat(record.getOverdraftLimit()).isEqualTo(new BigDecimal("500.00"));
                    assertThat(record.getLastStmtDate()).isEqualTo(LocalDate.of(2024, 2, 3));
                    assertThat(record.getNextStmtDate()).isEqualTo(LocalDate.of(2024, 3, 4));
                    assertThat(record.getAvailableBalance()).isEqualTo(new BigDecimal("1500.25"));
                    assertThat(record.getActualBalance()).isEqualTo(new BigDecimal("1499.75"));
                });
    }

    @Test
    void returnsNotFoundWhenAccountDoesNotExist() {
        UpdaccResult result = updaccService.update(new UpdaccRequest(12345678L, "ISA", new BigDecimal("2.25"), 500L));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Account number 12345678 was not found.");
    }

    @Test
    void invalidAccountTypeLeavesTheExistingRowUnchanged() {
        insertAccount();

        UpdaccResult result = updaccService.update(new UpdaccRequest(12345678L, " ISA", new BigDecimal("2.25"), 500L));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        assertThat(dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(12345678L))
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record).isNotNull();
                    assertThat(record.getAccountType()).isEqualTo("ISA");
                    assertThat(record.getInterestRate()).isEqualTo(new BigDecimal("1.50"));
                    assertThat(record.getOverdraftLimit()).isEqualTo(new BigDecimal("250.00"));
                });
    }

    private void insertAccount() {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, 10L)
                .set(CUSTOMER.NAME, "Example Customer")
                .set(CUSTOMER.ADDRESS, "1 Main Street")
                .set(CUSTOMER.DATE_OF_BIRTH, LocalDate.of(1990, 1, 1))
                .set(CUSTOMER.CREDIT_SCORE, (short) 500)
                .set(CUSTOMER.CS_REVIEW_DATE, LocalDate.of(2025, 1, 1))
                .execute();

        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.SORTCODE, "987654")
                .set(ACCOUNT.ACCOUNT_NUMBER, 12345678L)
                .set(ACCOUNT.CUSTOMER_NUMBER, 10L)
                .set(ACCOUNT.ACCOUNT_TYPE, "ISA")
                .set(ACCOUNT.INTEREST_RATE, new BigDecimal("1.50"))
                .set(ACCOUNT.OPENED, LocalDate.of(2024, 1, 2))
                .set(ACCOUNT.OVERDRAFT_LIMIT, new BigDecimal("250.00"))
                .set(ACCOUNT.LAST_STMT_DATE, LocalDate.of(2024, 2, 3))
                .set(ACCOUNT.NEXT_STMT_DATE, LocalDate.of(2024, 3, 4))
                .set(ACCOUNT.AVAILABLE_BALANCE, new BigDecimal("1500.25"))
                .set(ACCOUNT.ACTUAL_BALANCE, new BigDecimal("1499.75"))
                .execute();
    }
}