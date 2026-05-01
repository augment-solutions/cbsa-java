package com.augment.cbsa.web.dbcrfun;

import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DbcrfunControllerTest extends AbstractCockroachIntegrationTest {

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
    }

    @Test
    void updatesBalancesAndWritesPaymentAuditRows() throws Exception {
        insertCustomer(10L);
        insertAccount(10L, new BigDecimal("500.00"));

        mockMvc.perform(put("/api/v1/makepayment/dbcr").contentType(APPLICATION_JSON).content(requestJson("-25.00", 496)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PAYDBCR.CommAccno").value("12345678"))
                .andExpect(jsonPath("$.PAYDBCR.mSortC").value(987654))
                .andExpect(jsonPath("$.PAYDBCR.CommAvBal").value(475.00))
                .andExpect(jsonPath("$.PAYDBCR.CommActBal").value(475.00))
                .andExpect(jsonPath("$.PAYDBCR.CommSuccess").value("Y"))
                .andExpect(jsonPath("$.PAYDBCR.CommFailCode").value("0"));

        assertThat(dsl.select(ACCOUNT.AVAILABLE_BALANCE, ACCOUNT.ACTUAL_BALANCE)
                .from(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(12345678L))
                .fetchSingle())
                .extracting(record -> record.get(ACCOUNT.AVAILABLE_BALANCE), record -> record.get(ACCOUNT.ACTUAL_BALANCE))
                .containsExactly(new BigDecimal("475.00"), new BigDecimal("475.00"));

        Record4<String, String, BigDecimal, LocalDate> auditRow = dsl.select(
                        PROCTRAN.TRAN_TYPE,
                        PROCTRAN.DESCRIPTION,
                        PROCTRAN.AMOUNT,
                        PROCTRAN.TRAN_DATE
                )
                .from(PROCTRAN)
                .fetchSingle();
        assertThat(auditRow.value1()).isEqualTo("PDR");
        assertThat(auditRow.value2()).isEqualTo("ABCDEFGH123456");
        assertThat(auditRow.value3()).isEqualTo(new BigDecimal("-25.00"));
        assertThat(auditRow.value4()).isEqualTo(LocalDate.now(clock));
    }

    @Test
    void returnsConflictWhenPaymentWouldOverdrawAccount() throws Exception {
        insertCustomer(10L);
        insertAccount(10L, new BigDecimal("10.00"));

        mockMvc.perform(put("/api/v1/makepayment/dbcr").contentType(APPLICATION_JSON).content(requestJson("-25.00", 496)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.failCode").value("3"));

        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
        assertThat(dsl.select(ACCOUNT.AVAILABLE_BALANCE)
                .from(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq("987654"))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(12345678L))
                .fetchSingle(ACCOUNT.AVAILABLE_BALANCE))
                .isEqualTo(new BigDecimal("10.00"));
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

    private void insertAccount(long customerNumber, BigDecimal balance) {
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.SORTCODE, "987654")
                .set(ACCOUNT.ACCOUNT_NUMBER, 12345678L)
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

    private String requestJson(String amount, int facilityType) {
        return """
                {
                  "PAYDBCR": {
                    "CommAccno": "12345678",
                    "CommAmt": %s,
                    "mSortC": 0,
                    "CommAvBal": 0,
                    "CommActBal": 0,
                    "CommOrigin": {
                      "CommApplid": "ABCDEFGH",
                      "CommUserid": "12345678",
                      "CommFacilityName": "PAYAPI",
                      "CommNetwrkId": "NET00001",
                      "CommFaciltype": %d,
                      "Fill0": ""
                    },
                    "CommSuccess": " ",
                    "CommFailCode": " "
                  }
                }
                """.formatted(amount, facilityType);
    }
}