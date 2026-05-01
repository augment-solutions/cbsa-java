package com.augment.cbsa.web.updacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.UpdaccRequest;
import com.augment.cbsa.domain.UpdaccResult;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.UpdaccService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpdaccController.class)
@Import(CbsaExceptionHandler.class)
class UpdaccControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UpdaccService updaccService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(updaccService.update(request("MORTGAGE"))).thenReturn(UpdaccResult.success(new AccountDetails(
                "987654",
                1L,
                12345678L,
                "MORTGAGE",
                new BigDecimal("2.25"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("500.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("1500.25"),
                new BigDecimal("1499.75")
        )));

        mockMvc.perform(put("/api/v1/updacc/update").contentType(APPLICATION_JSON).content(requestJson("MORTGAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.UpdAcc.CommEye").value("ACCT"))
                .andExpect(jsonPath("$.UpdAcc.CommCustno").value("0000000001"))
                .andExpect(jsonPath("$.UpdAcc.CommScode").value("987654"))
                .andExpect(jsonPath("$.UpdAcc.CommAccno").value(12345678))
                .andExpect(jsonPath("$.UpdAcc.CommAccType").value("MORTGAGE"))
                .andExpect(jsonPath("$.UpdAcc.CommIntRate").value(2.25))
                .andExpect(jsonPath("$.UpdAcc.CommOverdraft").value(500))
                .andExpect(jsonPath("$.UpdAcc.CommSuccess").value("Y"));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(updaccService.update(request("MORTGAGE")))
                .thenReturn(UpdaccResult.failure("1", "Account number 12345678 was not found."));

        mockMvc.perform(put("/api/v1/updacc/update").contentType(APPLICATION_JSON).content(requestJson("MORTGAGE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsProblemDetailForValidationFailures() throws Exception {
        when(updaccService.update(request(" ISA")))
                .thenReturn(UpdaccResult.failure("2", "Account type must not be blank or start with a space."));

        mockMvc.perform(put("/api/v1/updacc/update").contentType(APPLICATION_JSON).content(requestJson(" ISA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid account type"))
                .andExpect(jsonPath("$.failCode").value("2"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(put("/api/v1/updacc/update").contentType(APPLICATION_JSON).content(missingAccountNumberJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    private UpdaccRequest request(String accountType) {
        return new UpdaccRequest(12345678L, accountType, new BigDecimal("2.25"), 500L);
    }

    private String requestJson(String accountType) {
        return """
                {
                  "UpdAcc": {
                    "CommEye": "ACCT",
                    "CommCustno": "0000000001",
                    "CommScode": "987654",
                    "CommAccno": 12345678,
                    "CommAccType": "%s",
                    "CommIntRate": 2.25,
                    "CommOpened": 2012024,
                    "CommOverdraft": 500,
                    "CommLastStmtDt": 3022024,
                    "CommNextStmtDt": 4032024,
                    "CommAvailBal": 1500.25,
                    "CommActualBal": 1499.75,
                    "CommSuccess": " "
                  }
                }
                """.formatted(accountType);
    }

    private String missingAccountNumberJson() {
        return """
                {
                  "UpdAcc": {
                    "CommAccType": "MORTGAGE",
                    "CommIntRate": 2.25,
                    "CommOverdraft": 500
                  }
                }
                """;
    }
}