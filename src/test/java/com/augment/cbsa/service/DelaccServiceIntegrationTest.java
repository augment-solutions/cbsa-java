package com.augment.cbsa.service;

import com.augment.cbsa.domain.DelaccRequest;
import com.augment.cbsa.domain.DelaccResult;
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
class DelaccServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private DelaccService delaccService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void deletesAccountAndWritesDeletionAudit() {
        insertCustomer(10L);
        insertAccount(10L, 12345678L, new BigDecimal("1499.75"));

        DelaccResult result = delaccService.delete(new DelaccRequest(12345678L));

        assertThat(result.deleteSuccess()).isTrue();
        assertThat(result.account()).isNotNull();
        assertThat(dsl.fetchCount(ACCOUNT)).isZero();
        assertThat(dsl.fetchCount(PROCTRAN)).isEqualTo(1);
        assertThat(dsl.select(PROCTRAN.TRAN_TYPE, PROCTRAN.DESCRIPTION, PROCTRAN.AMOUNT)
                .from(PROCTRAN)
                .fetchOne())
                .extracting(record -> record.get(PROCTRAN.TRAN_TYPE), record -> record.get(PROCTRAN.DESCRIPTION), record -> record.get(PROCTRAN.AMOUNT))
                .containsExactly("ODA", "0000000010ISA     0302202404032024DELETE", new BigDecimal("1499.75"));
    }

    @Test
    void returnsNotFoundWithoutWritingAuditRows() {
        DelaccResult result = delaccService.delete(new DelaccRequest(12345678L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
    }

    private void insertCustomer(long customerNumber) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, "Example Customer %d".formatted(customerNumber))
                .set(CUSTOMER.ADDRESS, "%d Example Road".formatted(customerNumber))
                .set(CUSTOMER.DATE_OF_BIRTH, LocalDate.of(1990, 1, 1))
                .set(CUSTOMER.CREDIT_SCORE, (short) 500)
                .set(CUSTOMER.CS_REVIEW_DATE, LocalDate.of(2025, 1, 1))
                .execute();
    }

    private void insertAccount(long customerNumber, long accountNumber, BigDecimal actualBalance) {
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.SORTCODE, "987654")
                .set(ACCOUNT.ACCOUNT_NUMBER, accountNumber)
                .set(ACCOUNT.CUSTOMER_NUMBER, customerNumber)
                .set(ACCOUNT.ACCOUNT_TYPE, "ISA")
                .set(ACCOUNT.INTEREST_RATE, new BigDecimal("1.50"))
                .set(ACCOUNT.OPENED, LocalDate.of(2024, 1, 2))
                .set(ACCOUNT.OVERDRAFT_LIMIT, new BigDecimal("250.00"))
                .set(ACCOUNT.LAST_STMT_DATE, LocalDate.of(2024, 2, 3))
                .set(ACCOUNT.NEXT_STMT_DATE, LocalDate.of(2024, 3, 4))
                .set(ACCOUNT.AVAILABLE_BALANCE, new BigDecimal("1500.25"))
                .set(ACCOUNT.ACTUAL_BALANCE, actualBalance)
                .execute();
    }
}
