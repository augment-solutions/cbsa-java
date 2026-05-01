package com.augment.cbsa.web.crecust;

import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.CrecustService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrecustController.class)
@Import(CbsaExceptionHandler.class)
class CrecustControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CrecustService crecustService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(crecustService.create(new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000))).thenReturn(
                CrecustResult.success(new CustomerDetails(
                        "987654",
                        1L,
                        "Dr Alice Example",
                        "1 Main Street",
                        LocalDate.of(2000, 1, 10),
                        430,
                        LocalDate.of(2026, 5, 8)
                ))
        );

        mockMvc.perform(post("/api/v1/crecust/insert")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson("Dr Alice Example", "1 Main Street", 10_01_2000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CreCust.CommEyecatcher").value("CUST"))
                .andExpect(jsonPath("$.CreCust.CommKey.CommSortcode").value(987654))
                .andExpect(jsonPath("$.CreCust.CommKey.CommNumber").value(1))
                .andExpect(jsonPath("$.CreCust.CommCreditScore").value(430));
    }

    @Test
    void returnsProblemDetailForValidationFailures() throws Exception {
        when(crecustService.create(new CrecustRequest("Alice Example", "1 Main Street", 10_01_2000)))
                .thenReturn(CrecustResult.failure("T", "The customer title is invalid."));

        mockMvc.perform(post("/api/v1/crecust/insert")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson("Alice Example", "1 Main Street", 10_01_2000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid customer title"))
                .andExpect(jsonPath("$.detail").value("The customer title is invalid."))
                .andExpect(jsonPath("$.failCode").value("T"));
    }

    @Test
    void returnsProblemDetailForCreditFailures() throws Exception {
        when(crecustService.create(new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000)))
                .thenReturn(CrecustResult.failure("G", "Credit check could not be completed."));

        mockMvc.perform(post("/api/v1/crecust/insert")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson("Dr Alice Example", "1 Main Street", 10_01_2000)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Credit check unavailable"))
                .andExpect(jsonPath("$.failCode").value("G"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/crecust/insert")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"CreCust":{"CommKey":{"CommSortcode":1000000,"CommNumber":0},"CommName":"Dr Alice Example","CommAddress":"1 Main Street","CommDateOfBirth":10012000}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void redactsAbendExceptionMessageFromResponseBody() throws Exception {
        when(crecustService.create(new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000)))
                .thenThrow(new CbsaAbendException("HWPT", "sensitive audit failure"));

        mockMvc.perform(post("/api/v1/crecust/insert")
                        .contentType(APPLICATION_JSON)
                        .content(requestJson("Dr Alice Example", "1 Main Street", 10_01_2000)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("HWPT"))
                .andExpect(content().string(not(containsString("sensitive audit failure"))));
    }

    private String requestJson(String name, String address, int dateOfBirth) {
        return """
                {
                  "CreCust": {
                    "CommEyecatcher": "CUST",
                    "CommKey": {"CommSortcode": 0, "CommNumber": 0},
                    "CommName": "%s",
                    "CommAddress": "%s",
                    "CommDateOfBirth": %d,
                    "CommCreditScore": 0,
                    "CommCsReviewDate": 0,
                    "CommSuccess": " ",
                    "CommFailCode": " "
                  }
                }
                """.formatted(name, address, dateOfBirth);
    }
}