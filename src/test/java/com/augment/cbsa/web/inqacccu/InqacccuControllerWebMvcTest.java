package com.augment.cbsa.web.inqacccu;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.InqacccuService;
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

@WebMvcTest(InqacccuController.class)
@Import(CbsaExceptionHandler.class)
class InqacccuControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InqacccuService inqacccuService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(inqacccuService.inquire(new InqacccuRequest(10L))).thenReturn(
                InqacccuResult.success(10L, java.util.List.of(new AccountDetails(
                        "987654",
                        10L,
                        100L,
                        "ISA",
                        new BigDecimal("1.50"),
                        LocalDate.of(2024, 1, 2),
                        new BigDecimal("250.00"),
                        LocalDate.of(2024, 2, 3),
                        LocalDate.of(2024, 3, 4),
                        new BigDecimal("1500.25"),
                        new BigDecimal("1499.75")
                )))
        );

        mockMvc.perform(get("/api/v1/inqacccu/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNumber").value(10))
                .andExpect(jsonPath("$.accountDetails[0].eye").value("ACCT"))
                .andExpect(jsonPath("$.accountDetails[0].opened").value(2012024));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(inqacccuService.inquire(new InqacccuRequest(10L))).thenReturn(
                InqacccuResult.failure("1", 10L, false, "Customer number 10 was not found.")
        );

        mockMvc.perform(get("/api/v1/inqacccu/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.detail").value("Customer number 10 was not found."))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void validationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/inqacccu/10000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(inqacccuService.inquire(new InqacccuRequest(10L)))
                .thenThrow(new CbsaAbendException("CVR1", "sensitive abend details"));

        mockMvc.perform(get("/api/v1/inqacccu/10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("CVR1"))
                .andExpect(content().string(not(containsString("sensitive abend details"))));
    }
}