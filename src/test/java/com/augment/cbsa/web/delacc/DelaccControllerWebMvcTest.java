package com.augment.cbsa.web.delacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DelaccRequest;
import com.augment.cbsa.domain.DelaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.DelaccService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DelaccController.class)
@Import(CbsaExceptionHandler.class)
@EnableConfigurationProperties(com.augment.cbsa.config.CbsaProperties.class)
@TestPropertySource(properties = "cbsa.sortcode=987654")
class DelaccControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DelaccService delaccService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(delaccService.delete(new DelaccRequest(12345678L))).thenReturn(DelaccResult.success(new AccountDetails(
                "987654",
                10L,
                12345678L,
                "ISA",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("1500.25"),
                new BigDecimal("1499.75")
        )));

        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DelAcc.DelAccEye").value("ACCT"))
                .andExpect(jsonPath("$.DelAcc.DelAccCustno").value("0000000010"))
                .andExpect(jsonPath("$.DelAcc.DelAccScode").value("987654"))
                .andExpect(jsonPath("$.DelAcc.DelAccAccno").value(12345678))
                .andExpect(jsonPath("$.DelAcc.DelAccDelSuccess").value("Y"));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(delaccService.delete(new DelaccRequest(12345678L)))
                .thenReturn(DelaccResult.failure("1", 12345678L, "Account number 12345678 was not found."));

        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsProblemDetailForDeleteFailures() throws Exception {
        when(delaccService.delete(new DelaccRequest(12345678L)))
                .thenReturn(DelaccResult.failure("3", 12345678L, "Account number 12345678 could not be deleted."));

        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Account deletion failed"))
                .andExpect(jsonPath("$.failCode").value("3"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(delete("/api/v1/delacc/remove/100000000").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void rejectsBodyAccnoThatMismatchesPath() throws Exception {
        String body = requestJson().replace("\"DelAccAccno\": 12345678", "\"DelAccAccno\": 99999999");
        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Body DelAccAccno does not match path accno."));
    }

    @Test
    void rejectsBodyScodeThatMismatchesConfiguredSortcode() throws Exception {
        String body = requestJson().replace("\"DelAccScode\": \"987654\"", "\"DelAccScode\": \"123456\"");
        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Body DelAccScode does not match the configured branch sortcode."));
    }

    @Test
    void allowsEmptyOrNullBodyKeyFields() throws Exception {
        when(delaccService.delete(new DelaccRequest(12345678L))).thenReturn(DelaccResult.success(new AccountDetails(
                "987654",
                10L,
                12345678L,
                "ISA",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("1500.25"),
                new BigDecimal("1499.75")
        )));

        String body = requestJson()
                .replace("\"DelAccAccno\": 12345678,", "")
                .replace("\"DelAccScode\": \"987654\"", "\"DelAccScode\": \"\"");
        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(delaccService.delete(new DelaccRequest(12345678L)))
                .thenThrow(new CbsaAbendException("HWPT", "sensitive audit failure"));

        mockMvc.perform(delete("/api/v1/delacc/remove/12345678").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("HWPT"))
                .andExpect(content().string(not(containsString("sensitive audit failure"))));
    }

    private String requestJson() {
        return """
                {
                  "DelAcc": {
                    "DelAccEye": "ACCT",
                    "DelAccCustno": "",
                    "DelAccScode": "987654",
                    "DelAccAccno": 12345678,
                    "DelAccAccType": "",
                    "DelAccIntRate": 0.00,
                    "DelAccOpened": 0,
                    "DelAccOverdraft": 0,
                    "DelAccLastStmtDt": 0,
                    "DelAccNextStmtDt": 0,
                    "DelAccAvailBal": 0.00,
                    "DelAccActualBal": 0.00,
                    "DelAccSuccess": " ",
                    "DelAccFailCd": " ",
                    "DelAccDelSuccess": " ",
                    "DelAccDelFailCd": " ",
                    "DelAccDelApplid": "",
                    "DelAccDelPcb1": "",
                    "DelAccDelPcb2": "",
                    "DelAccDelPcb3": ""
                  }
                }
                """;
    }
}