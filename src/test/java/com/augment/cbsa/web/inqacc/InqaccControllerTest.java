package com.augment.cbsa.web.inqacc;

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
class InqaccControllerTest extends AbstractCockroachIntegrationTest {

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
    void returnsAccountForSuccessfulLookup() throws Exception {
        insertAccount(10L, 12345678L, "ISA");

        mockMvc.perform(get("/api/v1/inqacc/12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eye").value("ACCT"))
                .andExpect(jsonPath("$.customerNumber").value(10))
                .andExpect(jsonPath("$.sortcode").value(987654))
                .andExpect(jsonPath("$.accountNumber").value(12345678))
                .andExpect(jsonPath("$.accountType").value("ISA"))
                .andExpect(jsonPath("$.interestRate").value(1.50))
                .andExpect(jsonPath("$.opened").value(2012024))
                .andExpect(jsonPath("$.availableBalance").value(1500.25))
                .andExpect(jsonPath("$.inquirySuccess").value("Y"));
    }

    @Test
    void returnsProblemDetailWhenAccountDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/inqacc/12345678"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Account number 12345678 was not found."))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsProblemDetailWhenNoAccountsExistInLastAccountMode() throws Exception {
        mockMvc.perform(get("/api/v1/inqacc/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("No accounts exist."))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void rejectsAccountNumbersOutsideTheCopybookRange() throws Exception {
        mockMvc.perform(get("/api/v1/inqacc/100000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"));
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