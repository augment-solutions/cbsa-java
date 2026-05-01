package com.augment.cbsa.web.creacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CreaccRequest;
import com.augment.cbsa.domain.CreaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.CreaccService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreaccController.class)
@Import(CbsaExceptionHandler.class)
class CreaccControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreaccService creaccService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(creaccService.create(request("ISA"))).thenReturn(CreaccResult.success(new AccountDetails(
                "987654", 10L, 1L, "ISA", new BigDecimal("1.50"), LocalDate.of(2026, 5, 1), new BigDecimal("250.00"), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), new BigDecimal("1500.25"), new BigDecimal("1499.75")
        )));

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 250L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CreAcc.CommEyecatcher").value("ACCT"))
                .andExpect(jsonPath("$.CreAcc.CommCustno").value(10))
                .andExpect(jsonPath("$.CreAcc.CommKey.CommSortcode").value("987654"))
                .andExpect(jsonPath("$.CreAcc.CommKey.CommNumber").value(1));
    }

    @Test
    void returnsProblemDetailForInvalidAccountTypes() throws Exception {
        when(creaccService.create(request("BONDS"))).thenReturn(CreaccResult.failure("A", "Account type must be ISA, MORTGAGE, SAVING, CURRENT, or LOAN."));

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("BONDS", 10L, 250L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid account type"))
                .andExpect(jsonPath("$.failCode").value("A"));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(creaccService.create(request("ISA"))).thenReturn(CreaccResult.failure("1", "Customer number 10 was not found."));

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 250L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 100_000_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(creaccService.create(request("ISA"))).thenThrow(new CbsaAbendException("HWPT", "sensitive audit failure"));

        mockMvc.perform(post("/api/v1/creacc/insert").contentType(APPLICATION_JSON).content(requestJson("ISA", 10L, 250L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("HWPT"))
                .andExpect(content().string(not(containsString("sensitive audit failure"))));
    }

    private CreaccRequest request(String accountType) {
        return new CreaccRequest(10L, accountType, new BigDecimal("1.50"), 250L, new BigDecimal("1500.25"), new BigDecimal("1499.75"));
    }

    private String requestJson(String accountType, long customerNumber, long overdraftLimit) {
        return """
                {
                  "CreAcc": {
                    "CommEyecatcher": "ACCT",
                    "CommCustno": %d,
                    "CommKey": {"CommSortcode": "000000", "CommNumber": 0},
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