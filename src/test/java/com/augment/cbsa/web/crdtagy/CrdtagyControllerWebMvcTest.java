package com.augment.cbsa.web.crdtagy;

import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.CreditAgencyService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrdtagyController.class)
@Import(CbsaExceptionHandler.class)
class CrdtagyControllerWebMvcTest {

    private static final CrecustRequest REQUEST = new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreditAgencyService creditAgencyService;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void returnsSuccessfulResponseForEveryAgencyRoute(int agencyNumber) throws Exception {
        when(creditAgencyService.requestCreditScore(eq(REQUEST), eq(agencyNumber)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(450 + agencyNumber)));

        mockMvc.perform(post("/api/v1/crdtagy/{agencyNumber}", agencyNumber)
                        .contentType(APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CreCust.CommEyecatcher").value("CUST"))
                .andExpect(jsonPath("$.CreCust.CommKey.CommSortcode").value("987654"))
                .andExpect(jsonPath("$.CreCust.CommCreditScore").value(450 + agencyNumber));
    }

    @Test
    void rejectsOutOfRangeAgencyNumbers() throws Exception {
        mockMvc.perform(post("/api/v1/crdtagy/{agencyNumber}", 6)
                        .contentType(APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/crdtagy/{agencyNumber}", 1)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"CreCust":{"CommKey":{"CommSortcode":"1000000","CommNumber":0},"CommName":"Dr Alice Example","CommAddress":"1 Main Street","CommDateOfBirth":10012000}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void redactsAbendExceptionMessageFromAsyncFailures() throws Exception {
        when(creditAgencyService.requestCreditScore(eq(REQUEST), eq(3)))
                .thenReturn(CompletableFuture.failedFuture(new CbsaAbendException("PLOP", "sensitive delay failure")));

        mockMvc.perform(post("/api/v1/crdtagy/{agencyNumber}", 3)
                        .contentType(APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Service abend"))
                .andExpect(jsonPath("$.abendCode").value("PLOP"))
                .andExpect(content().string(not(containsString("sensitive delay failure"))));
    }

    private String requestJson() {
        return """
                {
                  "CreCust": {
                    "CommEyecatcher": "CUST",
                    "CommKey": {"CommSortcode": "987654", "CommNumber": 42},
                    "CommName": "Dr Alice Example",
                    "CommAddress": "1 Main Street",
                    "CommDateOfBirth": 10012000,
                    "CommCreditScore": 0,
                    "CommCsReviewDate": 0,
                    "CommSuccess": " ",
                    "CommFailCode": " "
                  }
                }
                """;
    }
}