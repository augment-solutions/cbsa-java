package com.augment.cbsa.web.delcus;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.DelcusRequest;
import com.augment.cbsa.domain.DelcusResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.DelcusService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DelcusController.class)
@Import(CbsaExceptionHandler.class)
class DelcusControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DelcusService delcusService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(delcusService.delete(new DelcusRequest(1L))).thenReturn(DelcusResult.success(new CustomerDetails(
                "987654",
                1L,
                "Mr Alice Example",
                "1 Main Street",
                LocalDate.of(2000, 1, 10),
                430,
                LocalDate.of(2026, 5, 8)
        )));

        mockMvc.perform(delete("/api/v1/delcus/remove/1").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DelCus.CommEye").value("CUST"))
                .andExpect(jsonPath("$.DelCus.CommScode").value("987654"))
                .andExpect(jsonPath("$.DelCus.CommCustno").value("0000000001"))
                .andExpect(jsonPath("$.DelCus.CommDelSuccess").value("Y"));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(delcusService.delete(new DelcusRequest(1L)))
                .thenReturn(DelcusResult.failure("1", 1L, "Customer number 1 was not found."));

        mockMvc.perform(delete("/api/v1/delcus/remove/1").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(delete("/api/v1/delcus/remove/abc").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(delcusService.delete(new DelcusRequest(1L)))
                .thenThrow(new CbsaAbendException("WPV7", "sensitive delete details"));

        mockMvc.perform(delete("/api/v1/delcus/remove/1").contentType(APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("WPV7"))
                .andExpect(content().string(not(containsString("sensitive delete details"))));
    }

    private String requestJson() {
        return """
                {
                  "DelCus": {
                    "CommEye": "CUST",
                    "CommScode": "987654",
                    "CommName": "",
                    "CommAddr": "",
                    "CommDob": 0,
                    "CommCreditScore": 0,
                    "CommCsReviewDate": 0,
                    "CommDelSuccess": " ",
                    "CommDelFailCd": " "
                  }
                }
                """;
    }
}