package com.augment.cbsa.web.inqcust;

import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.InqcustService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InqcustController.class)
@Import(CbsaExceptionHandler.class)
class InqcustControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InqcustService inqcustService;

    @Test
    void returnsServiceUnavailableWhenRandomSelectionExhaustsRetries() throws Exception {
        when(inqcustService.inquire(new InqcustRequest(0L))).thenReturn(
                InqcustResult.failure("R", 0L, "Unable to find a random customer after exhausting retry attempts.")
        );

        mockMvc.perform(get("/api/v1/inqcust/0"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.failCode").value("R"))
                .andExpect(jsonPath("$.message").value("Unable to find a random customer after exhausting retry attempts."));
    }

    @Test
    void returnsGenericUnexpectedErrorMessage() throws Exception {
        when(inqcustService.inquire(new InqcustRequest(1L))).thenThrow(new IllegalStateException("sensitive details"));

        mockMvc.perform(get("/api/v1/inqcust/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andExpect(jsonPath("$.abendCode").value("UNEX"));
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(inqcustService.inquire(new InqcustRequest(2L)))
                .thenThrow(new CbsaAbendException("CVR1", "sensitive abend details"));

        mockMvc.perform(get("/api/v1/inqcust/2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("CVR1"))
                .andExpect(content().string(not(containsString("sensitive abend details"))));
    }
}