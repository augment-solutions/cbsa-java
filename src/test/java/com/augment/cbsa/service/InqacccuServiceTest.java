package com.augment.cbsa.service;

import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
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
class InqacccuServiceTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private InqacccuService inqacccuService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void returnsAllAccountsForExistingCustomerWithoutFilteringBlankTypes() {
        insertCustomer(10L);
        insertAccount(10L, 200L, "");
        insertAccount(10L, 100L, "ISA");

        InqacccuResult result = inqacccuService.inquire(new InqacccuRequest(10L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.customerFound()).isTrue();
        assertThat(result.accounts()).extracting(account -> account.accountNumber()).containsExactly(100L, 200L);
        assertThat(result.accounts()).extracting(account -> account.accountType()).containsExactly("ISA", "");
    }

    @Test
    void returnsSuccessfulEmptyResultWhenCustomerHasNoAccounts() {
        insertCustomer(10L);

        InqacccuResult result = inqacccuService.inquire(new InqacccuRequest(10L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.customerFound()).isTrue();
        assertThat(result.accounts()).isEmpty();
    }

    @Test
    void returnsNotFoundWhenCustomerDoesNotExist() {
        InqacccuResult result = inqacccuService.inquire(new InqacccuRequest(1234567890L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Customer number 1234567890 was not found.");
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