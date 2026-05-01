package com.augment.cbsa.web.inqacc;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqaccRequest;
import com.augment.cbsa.domain.InqaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.InqaccService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InqaccController.class)
@Import(CbsaExceptionHandler.class)
class InqaccControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InqaccService inqaccService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(inqaccService.inquire(new InqaccRequest(12345678L))).thenReturn(
                InqaccResult.success(new AccountDetails(
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
                ))
        );

        mockMvc.perform(get("/api/v1/inqacc/12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eye").value("ACCT"))
                .andExpect(jsonPath("$.accountNumber").value(12345678))
                .andExpect(jsonPath("$.opened").value(2012024));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(inqaccService.inquire(new InqaccRequest(12345678L))).thenReturn(
                InqaccResult.failure("1", 12345678L, "Account number 12345678 was not found.")
        );

        mockMvc.perform(get("/api/v1/inqacc/12345678"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Account number 12345678 was not found."))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void validationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/inqacc/100000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void returnsGenericUnexpectedErrorMessage() throws Exception {
        when(inqaccService.inquire(new InqaccRequest(12345678L))).thenThrow(new IllegalStateException("sensitive details"));

        mockMvc.perform(get("/api/v1/inqacc/12345678"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andExpect(jsonPath("$.abendCode").value("UNEX"));
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(inqaccService.inquire(new InqaccRequest(12345678L)))
                .thenThrow(new CbsaAbendException("HRAC", "sensitive abend details"));

        mockMvc.perform(get("/api/v1/inqacc/12345678"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("HRAC"))
                .andExpect(content().string(not(containsString("sensitive abend details"))));
    }
}