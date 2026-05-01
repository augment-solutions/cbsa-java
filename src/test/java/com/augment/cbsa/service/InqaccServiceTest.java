package com.augment.cbsa.service;

import com.augment.cbsa.domain.InqaccRequest;
import com.augment.cbsa.domain.InqaccResult;
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
class InqaccServiceTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private InqaccService inqaccService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void returnsAccountForExactAccountNumber() {
        insertAccount(10L, 12345678L, "ISA");

        InqaccResult result = inqaccService.inquire(new InqaccRequest(12345678L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.failCode()).isEqualTo("0");
        assertThat(result.account()).isNotNull();
        assertThat(result.account().customerNumber()).isEqualTo(10L);
        assertThat(result.account().interestRate()).isEqualTo(new BigDecimal("1.50"));
    }

    @Test
    void exactLookupReturnsNotFoundWhenAccountDoesNotExist() {
        InqaccResult result = inqaccService.inquire(new InqaccRequest(12345678L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Account number 12345678 was not found.");
    }

    @Test
    void lastAccountModeReturnsHighestAccountNumber() {
        insertAccount(10L, 12345678L, "ISA");
        insertAccount(11L, 23456789L, "MORTGAGE");

        InqaccResult result = inqaccService.inquire(new InqaccRequest(99_999_999L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountNumber()).isEqualTo(23_456_789L);
    }

    @Test
    void lastAccountModeReturnsNotFoundWhenNoAccountsExist() {
        InqaccResult result = inqaccService.inquire(new InqaccRequest(99_999_999L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("No accounts exist.");
    }

    @Test
    void blankAccountTypeStillReturnsAccount() {
        // COBOL INQACC.cbl does not filter rows by account_type; blank-type
        // accounts are returned as-is from both exact and last-mode lookups.
        insertAccount(10L, 12345678L, "");

        InqaccResult result = inqaccService.inquire(new InqaccRequest(12345678L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.failCode()).isEqualTo("0");
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountType()).isEmpty();
    }

    private void insertAccount(long customerNumber, long accountNumber, String accountType) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, "Example Customer %d".formatted(customerNumber))
                .set(CUSTOMER.ADDRESS, "%d Example Road".formatted(customerNumber))
                .set(CUSTOMER.DATE_OF_BIRTH, LocalDate.of(1990, 1, 1))
                .set(CUSTOMER.CREDIT_SCORE, (short) 500)
                .set(CUSTOMER.CS_REVIEW_DATE, LocalDate.of(2025, 1, 1))
                .execute();

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