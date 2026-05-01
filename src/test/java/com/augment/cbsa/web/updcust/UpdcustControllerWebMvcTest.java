package com.augment.cbsa.web.updcust;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.UpdcustService;
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

@WebMvcTest(UpdcustController.class)
@Import(CbsaExceptionHandler.class)
class UpdcustControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UpdcustService updcustService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(updcustService.update(request("0000000001", "Mrs Alice Example", "1 Main Street"))).thenReturn(
                UpdcustResult.success(new CustomerDetails(
                        "987654",
                        1L,
                        "Mrs Alice Example",
                        "1 Main Street",
                        LocalDate.of(2000, 1, 10),
                        430,
                        LocalDate.of(2026, 5, 8)
                ))
        );

        mockMvc.perform(put("/api/v1/updcust/update").contentType(APPLICATION_JSON).content(requestJson("0000000001", "Mrs Alice Example", "1 Main Street")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.UpdCust.CommEye").value("CUST"))
                .andExpect(jsonPath("$.UpdCust.CommScode").value("987654"))
                .andExpect(jsonPath("$.UpdCust.CommCustno").value("0000000001"))
                .andExpect(jsonPath("$.UpdCust.CommName").value("Mrs Alice Example"))
                .andExpect(jsonPath("$.UpdCust.CommCreditScore").value(430));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(updcustService.update(request("0000000001", "Mrs Alice Example", "1 Main Street")))
                .thenReturn(UpdcustResult.failure("1", "Customer number 1 was not found."));

        mockMvc.perform(put("/api/v1/updcust/update").contentType(APPLICATION_JSON).content(requestJson("0000000001", "Mrs Alice Example", "1 Main Street")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsProblemDetailForValidationFailures() throws Exception {
        when(updcustService.update(request("0000000001", "Reverend Alice Example", "1 Main Street")))
                .thenReturn(UpdcustResult.failure("T", "The customer title is invalid."));

        mockMvc.perform(put("/api/v1/updcust/update").contentType(APPLICATION_JSON).content(requestJson("0000000001", "Reverend Alice Example", "1 Main Street")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid customer title"))
                .andExpect(jsonPath("$.failCode").value("T"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(put("/api/v1/updcust/update").contentType(APPLICATION_JSON).content(requestJson("abc", "Mrs Alice Example", "1 Main Street")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    private UpdcustRequest request(String customerNumber, String name, String address) {
        return new UpdcustRequest(Long.parseLong(customerNumber), name, address, 10_01_2000, 430, 8_052_026);
    }

    private String requestJson(String customerNumber, String name, String address) {
        return """
                {
                  "UpdCust": {
                    "CommEye": "CUST",
                    "CommScode": "987654",
                    "CommCustno": "%s",
                    "CommName": "%s",
                    "CommAddress": "%s",
                    "CommDob": 10012000,
                    "CommCreditScore": 430,
                    "CommCsReviewDate": 8052026,
                    "CommUpdSuccess": " ",
                    "CommUpdFailCd": " "
                  }
                }
                """.formatted(customerNumber, name, address);
    }
}
