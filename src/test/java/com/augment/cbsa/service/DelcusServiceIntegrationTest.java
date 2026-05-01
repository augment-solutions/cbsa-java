package com.augment.cbsa.service;

import com.augment.cbsa.domain.DelcusRequest;
import com.augment.cbsa.domain.DelcusResult;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DelcusServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private DelcusService delcusService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void deletesCustomerAccountsAndWritesAuditRows() {
        insertCustomer(10L, "Mr Alice Example");
        insertAccount(10L, 100L, "ISA", new BigDecimal("1499.75"));
        insertAccount(10L, 200L, "SAVINGS", new BigDecimal("500.00"));

        DelcusResult result = delcusService.delete(new DelcusRequest(10L));

        assertThat(result.deleteSuccess()).isTrue();
        assertThat(result.customer()).isNotNull();
        assertThat(dsl.fetchCount(CUSTOMER)).isZero();
        assertThat(dsl.fetchCount(ACCOUNT)).isZero();
        assertThat(dsl.fetchCount(PROCTRAN)).isEqualTo(3);

        Result<?> auditRows = dsl.select(PROCTRAN.TRAN_TYPE, PROCTRAN.DESCRIPTION, PROCTRAN.AMOUNT)
                .from(PROCTRAN)
                .orderBy(PROCTRAN.COUNTER.asc())
                .fetch();

        assertThat(auditRows.getValues(PROCTRAN.TRAN_TYPE)).containsExactly("ODA", "ODA", "ODC");
        assertThat(auditRows.get(0).get(PROCTRAN.DESCRIPTION)).isEqualTo("0000000010ISA     0302202404032024DELETE");
        assertThat(auditRows.get(1).get(PROCTRAN.DESCRIPTION)).isEqualTo("0000000010SAVINGS 0302202404032024DELETE");
        assertThat(auditRows.get(2).get(PROCTRAN.DESCRIPTION)).startsWith("9876540000000010Mr Alice Examp10/01/2000");
        assertThat(auditRows.get(0).get(PROCTRAN.AMOUNT)).isEqualTo(new BigDecimal("1499.75"));
        assertThat(auditRows.get(2).get(PROCTRAN.AMOUNT)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void returnsNotFoundWithoutWritingAuditRows() {
        DelcusResult result = delcusService.delete(new DelcusRequest(10L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
    }

    private void insertCustomer(long customerNumber, String name) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, name)
                .set(CUSTOMER.ADDRESS, "1 Main Street")
                .set(CUSTOMER.DATE_OF_BIRTH, LocalDate.of(2000, 1, 10))
                .set(CUSTOMER.CREDIT_SCORE, (short) 430)
                .set(CUSTOMER.CS_REVIEW_DATE, LocalDate.of(2026, 5, 8))
                .execute();
    }

    private void insertAccount(long customerNumber, long accountNumber, String accountType, BigDecimal actualBalance) {
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
                .set(ACCOUNT.AVAILABLE_BALANCE, actualBalance)
                .set(ACCOUNT.ACTUAL_BALANCE, actualBalance)
                .execute();
    }
}
