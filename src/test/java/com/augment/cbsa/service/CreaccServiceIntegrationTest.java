package com.augment.cbsa.service;

import com.augment.cbsa.domain.CreaccRequest;
import com.augment.cbsa.domain.CreaccResult;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CONTROL;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CreaccServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private CreaccService creaccService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
        dsl.update(CONTROL)
                .set(CONTROL.CUSTOMER_COUNT, 0L)
                .set(CONTROL.CUSTOMER_LAST, 0L)
                .set(CONTROL.ACCOUNT_COUNT, 0L)
                .set(CONTROL.ACCOUNT_LAST, 0L)
                .where(CONTROL.ID.eq("GLOBAL"))
                .execute();
    }

    @Test
    void createsAccountAuditTrailAndControlRow() {
        insertCustomer(10L);
        LocalDate today = LocalDate.now(Clock.systemUTC());

        CreaccResult result = creaccService.create(request("ISA"));

        assertThat(result.creationSuccess()).isTrue();
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountNumber()).isEqualTo(1L);
        assertThat(result.account().opened()).isEqualTo(today);
        assertThat(result.account().nextStatementDate()).isEqualTo(today.plusDays(30));
        assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(1);
        assertThat(dsl.fetchCount(PROCTRAN)).isEqualTo(1);
        assertThat(dsl.select(CONTROL.ACCOUNT_COUNT, CONTROL.ACCOUNT_LAST)
                .from(CONTROL)
                .where(CONTROL.ID.eq("GLOBAL"))
                .fetchOne())
                .extracting(r -> r.get(CONTROL.ACCOUNT_COUNT), r -> r.get(CONTROL.ACCOUNT_LAST))
                .containsExactly(1L, 1L);
        assertThat(dsl.select(PROCTRAN.DESCRIPTION).from(PROCTRAN).fetchSingle(PROCTRAN.DESCRIPTION))
                .isEqualTo("0000000010ISA     " + today.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"))
                        + today.plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")) + "      ");
    }

    @Test
    void rejectsUnsupportedAccountTypesWithoutPersistingAnything() {
        insertCustomer(10L);

        CreaccResult result = creaccService.create(request("BONDS"));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("A");
        assertThat(dsl.fetchCount(ACCOUNT)).isZero();
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
        assertThat(dsl.select(CONTROL.ACCOUNT_COUNT, CONTROL.ACCOUNT_LAST).from(CONTROL).where(CONTROL.ID.eq("GLOBAL")).fetchOne())
                .extracting(r -> r.get(CONTROL.ACCOUNT_COUNT), r -> r.get(CONTROL.ACCOUNT_LAST))
                .containsExactly(0L, 0L);
    }

    @Test
    void rejectsCustomersWhoAlreadyHaveTenAccounts() {
        insertCustomer(10L);
        for (long accountNumber = 1; accountNumber <= 10; accountNumber++) {
            insertAccount(10L, accountNumber, "ISA");
        }

        CreaccResult result = creaccService.create(request("ISA"));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("8");
        assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(10);
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
    }

    private CreaccRequest request(String accountType) {
        return new CreaccRequest(10L, accountType, new BigDecimal("1.50"), 250L, new BigDecimal("1500.25"), new BigDecimal("1499.75"));
    }

    private void insertCustomer(long customerNumber) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, "Example Customer")
                .set(CUSTOMER.ADDRESS, "1 Example Road")
                .set(CUSTOMER.DATE_OF_BIRTH, LocalDate.of(1990, 1, 1))
                .set(CUSTOMER.CREDIT_SCORE, (short) 500)
                .set(CUSTOMER.CS_REVIEW_DATE, LocalDate.of(2025, 1, 1))
                .execute();
    }

    private void insertAccount(long customerNumber, long accountNumber, String accountType) {
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.SORTCODE, "987654")
                .set(ACCOUNT.ACCOUNT_NUMBER, accountNumber)
                .set(ACCOUNT.CUSTOMER_NUMBER, customerNumber)
                .set(ACCOUNT.ACCOUNT_TYPE, accountType)
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