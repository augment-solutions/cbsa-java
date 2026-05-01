package com.augment.cbsa.web.inqcust;

import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
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
class InqcustControllerTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void returnsCustomerForSuccessfulLookup() throws Exception {
        insertCustomer(10L, "Dr William Q Price", "19 Nutmeg Grove, Durham", LocalDate.of(1936, 9, 24), (short) 263, LocalDate.of(2022, 2, 9));

        mockMvc.perform(get("/api/v1/inqcust/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eye").value("CUST"))
                .andExpect(jsonPath("$.sortcode").value("987654"))
                .andExpect(jsonPath("$.customerNumber").value(10))
                .andExpect(jsonPath("$.name").value("Dr William Q Price"))
                .andExpect(jsonPath("$.dateOfBirth.day").value(24))
                .andExpect(jsonPath("$.creditScoreReviewDate.year").value(2022))
                .andExpect(jsonPath("$.inquirySuccess").value("Y"))
                .andExpect(jsonPath("$.failCode").value("0"));
    }

    @Test
    void returnsNotFoundResponseWhenCustomerDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/inqcust/1234567890"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.failCode").value("1"))
                .andExpect(jsonPath("$.detail").value("Customer number 1234567890 was not found."));
    }

    @Test
    void returnsNotFoundResponseWhenNoCustomersExistForSpecialLookupModes() throws Exception {
        mockMvc.perform(get("/api/v1/inqcust/0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.failCode").value("1"))
                .andExpect(jsonPath("$.detail").value("No customers exist."));

        mockMvc.perform(get("/api/v1/inqcust/9999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.failCode").value("1"))
                .andExpect(jsonPath("$.detail").value("No customers exist."));
    }

    @Test
    void rejectsCustomerNumbersOutsideTheCopybookRange() throws Exception {
        mockMvc.perform(get("/api/v1/inqcust/10000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"));
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