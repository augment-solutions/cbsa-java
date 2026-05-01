package com.augment.cbsa.web.inqacccu;

import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InqacccuControllerTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void returnsAccountsForSuccessfulLookup() throws Exception {
        insertCustomer(10L);
        insertAccount(10L, 100L, "ISA");
        insertAccount(10L, 200L, "");

        mockMvc.perform(get("/api/v1/inqacccu/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNumber").value(10))
                .andExpect(jsonPath("$.inquirySuccess").value("Y"))
                .andExpect(jsonPath("$.customerFound").value("Y"))
                .andExpect(jsonPath("$.accountDetails[0].eye").value("ACCT"))
                .andExpect(jsonPath("$.accountDetails[0].sortcode").value("987654"))
                .andExpect(jsonPath("$.accountDetails[0].accountNumber").value(100))
                .andExpect(jsonPath("$.accountDetails[1].accountType").value(""));
    }

    @Test
    void returnsProblemDetailWhenCustomerDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/inqacccu/1234567890"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.detail").value("Customer number 1234567890 was not found."))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsSuccessfulEmptyArrayWhenCustomerHasNoAccounts() throws Exception {
        insertCustomer(10L);

        mockMvc.perform(get("/api/v1/inqacccu/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerFound").value("Y"))
                .andExpect(jsonPath("$.accountDetails").isArray());
    }

    @Test
    void rejectsCustomerNumbersOutsideTheCopybookRange() throws Exception {
        mockMvc.perform(get("/api/v1/inqacccu/10000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"));
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