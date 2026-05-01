package com.augment.cbsa.service;

import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
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
class InqcustServiceTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private InqcustService inqcustService;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(CUSTOMER).execute();
        dsl.deleteFrom(CONTROL).execute();
    }

    @Test
    void returnsCustomerForExactCustomerNumber() {
        insertCustomer(10L, "Dr William Q Price", "19 Nutmeg Grove, Durham", LocalDate.of(1936, 9, 24), (short) 263, LocalDate.of(2022, 2, 9));

        InqcustResult result = inqcustService.inquire(new InqcustRequest(10L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.failCode()).isEqualTo("0");
        assertThat(result.customer()).isNotNull();
        assertThat(result.customer().name()).isEqualTo("Dr William Q Price");
        assertThat(result.customer().customerNumber()).isEqualTo(10L);
    }

    @Test
    void randomCustomerModeAlwaysReturnsAnExistingCustomer() {
        insertCustomer(10L, "First Customer", "1 Example Road", LocalDate.of(1980, 1, 2), (short) 120, LocalDate.of(2024, 5, 6));
        insertCustomer(25L, "Last Customer", "99 Example Road", LocalDate.of(1990, 7, 8), (short) 640, LocalDate.of(2025, 3, 4));

        InqcustResult result = inqcustService.inquire(new InqcustRequest(0L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.customer()).isNotNull();
        assertThat(result.customer().customerNumber()).isIn(10L, 25L);
        assertThat(result.customer().creditScore()).isBetween(0, 999);
    }

    @Test
    void lastCustomerModeReturnsTheHighestCustomerNumber() {
        insertCustomer(10L, "First Customer", "1 Example Road", LocalDate.of(1980, 1, 2), (short) 120, LocalDate.of(2024, 5, 6));
        insertCustomer(25L, "Last Customer", "99 Example Road", LocalDate.of(1990, 7, 8), (short) 640, LocalDate.of(2025, 3, 4));

        InqcustResult result = inqcustService.inquire(new InqcustRequest(9_999_999_999L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.customer()).isNotNull();
        assertThat(result.customer().customerNumber()).isEqualTo(25L);
    }

    private void insertCustomer(long customerNumber, String name, String address, LocalDate dateOfBirth, short creditScore, LocalDate csReviewDate) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, name)
                .set(CUSTOMER.ADDRESS, address)
                .set(CUSTOMER.DATE_OF_BIRTH, dateOfBirth)
                .set(CUSTOMER.CREDIT_SCORE, creditScore)
                .set(CUSTOMER.CS_REVIEW_DATE, csReviewDate)
                .execute();
    }
}