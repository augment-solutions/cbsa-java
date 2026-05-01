package com.augment.cbsa.service;

import com.augment.cbsa.domain.XfrfunRequest;
import com.augment.cbsa.domain.XfrfunResult;
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
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class XfrfunServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private XfrfunService xfrfunService;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private Clock clock;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void transfersFundsAndWritesTransferAudit() {
        insertCustomer(10L);
        insertCustomer(20L);
        insertAccount(10L, 12345678L, new BigDecimal("500.00"));
        insertAccount(20L, 87654321L, new BigDecimal("150.00"));

        LocalDate today = LocalDate.now(clock);
        XfrfunResult result = xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00")));

        assertThat(result.transferSuccess()).isTrue();
        assertThat(result.fromAvailableBalance()).isEqualByComparingTo("475.00");
        assertThat(result.toAvailableBalance()).isEqualByComparingTo("175.00");

        assertThat(dsl.select(ACCOUNT.AVAILABLE_BALANCE)
                .from(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(12345678L))
                .fetchSingle(ACCOUNT.AVAILABLE_BALANCE)).isEqualTo(new BigDecimal("475.00"));
        assertThat(dsl.select(ACCOUNT.AVAILABLE_BALANCE)
                .from(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(87654321L))
                .fetchSingle(ACCOUNT.AVAILABLE_BALANCE)).isEqualTo(new BigDecimal("175.00"));

        var auditRow = dsl.select(PROCTRAN.TRAN_TYPE, PROCTRAN.DESCRIPTION, PROCTRAN.AMOUNT, PROCTRAN.TRAN_DATE)
                .from(PROCTRAN)
                .fetchSingle();
        assertThat(auditRow.get(PROCTRAN.TRAN_TYPE)).isEqualTo("TFR");
        assertThat(auditRow.get(PROCTRAN.DESCRIPTION)).isEqualTo(String.format("%-26.26s%s%08d", "TRANSFER", "987654", 87654321L));
        assertThat(auditRow.get(PROCTRAN.AMOUNT)).isEqualTo(new BigDecimal("25.00"));
        assertThat(auditRow.get(PROCTRAN.TRAN_DATE)).isIn(today, today.plusDays(1));
    }

    @Test
    void returnsToAccountNotFoundWithoutChangingTheFromAccount() {
        insertCustomer(10L);
        insertAccount(10L, 12345678L, new BigDecimal("500.00"));

        XfrfunResult result = xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00")));

        assertThat(result.transferSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        assertThat(dsl.select(ACCOUNT.AVAILABLE_BALANCE)
                .from(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(12345678L))
                .fetchSingle(ACCOUNT.AVAILABLE_BALANCE)).isEqualTo(new BigDecimal("500.00"));
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
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

    private void insertAccount(long customerNumber, long accountNumber, BigDecimal balance) {
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
                .set(ACCOUNT.AVAILABLE_BALANCE, balance)
                .set(ACCOUNT.ACTUAL_BALANCE, balance)
                .execute();
    }
}