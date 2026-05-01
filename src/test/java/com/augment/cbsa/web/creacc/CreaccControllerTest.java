package com.augment.cbsa.web.creacc;

import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CONTROL;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CreaccControllerTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private Clock clock;

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
    void returnsAccountForSuccessfulCreation() throws Exception {
        insertCustomer(10L);
        LocalDate today = LocalDate.now(clock);

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 250L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CreAcc.CommEyecatcher").value("ACCT"))
                .andExpect(jsonPath("$.CreAcc.CommCustno").value(10))
                .andExpect(jsonPath("$.CreAcc.CommKey.CommSortcode").value(987654))
                .andExpect(jsonPath("$.CreAcc.CommKey.CommNumber").value(1))
                .andExpect(jsonPath("$.CreAcc.CommOpened").value(Integer.parseInt(today.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")))))
                .andExpect(jsonPath("$.CreAcc.CommNextStmtDt").value(Integer.parseInt(today.plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")))));

        org.assertj.core.api.Assertions.assertThat(dsl.fetchCount(ACCOUNT)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(dsl.fetchCount(PROCTRAN)).isEqualTo(1);
    }

    @Test
    void returnsNotFoundWhenCustomerDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 250L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsConflictWhenCustomerAlreadyHasTenAccounts() throws Exception {
        insertCustomer(10L);
        for (long accountNumber = 1; accountNumber <= 10; accountNumber++) {
            insertAccount(10L, accountNumber, "ISA");
        }

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 250L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Maximum account count reached"))
                .andExpect(jsonPath("$.failCode").value("8"));
    }

    @Test
    void rejectsFieldsOutsideTheCopybookRange() throws Exception {
        insertCustomer(10L);

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 100_000_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"));
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

    private String requestJson(String accountType, long customerNumber, long overdraftLimit) {
        return """
                {
                  "CreAcc": {
                    "CommEyecatcher": "ACCT",
                    "CommCustno": %d,
                    "CommKey": {"CommSortcode": 0, "CommNumber": 0},
                    "CommAccType": "%s",
                    "CommIntRt": 1.50,
                    "CommOpened": 0,
                    "CommOverdrLim": %d,
                    "CommLastStmtDt": 0,
                    "CommNextStmtDt": 0,
                    "CommAvailBal": 1500.25,
                    "CommActBal": 1499.75,
                    "CommSuccess": " ",
                    "CommFailCode": " "
                  }
                }
                """.formatted(customerNumber, accountType, overdraftLimit);
    }
}