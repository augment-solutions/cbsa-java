package com.augment.cbsa.web.xfrfun;

import com.augment.cbsa.domain.XfrfunRequest;
import com.augment.cbsa.domain.XfrfunResult;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.XfrfunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(XfrfunController.class)
@Import(CbsaExceptionHandler.class)
@TestPropertySource(properties = "cbsa.sortcode=987654")
class XfrfunControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private XfrfunService xfrfunService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00"))))
                .thenReturn(XfrfunResult.success(
                        new BigDecimal("475.00"),
                        new BigDecimal("475.00"),
                        new BigDecimal("175.00"),
                        new BigDecimal("175.00")
                ));

        mockMvc.perform(post("/api/v1/xfrfun").contentType(APPLICATION_JSON).content(requestJson("25.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.XFRFUN.CommFaccno").value(12345678))
                .andExpect(jsonPath("$.XFRFUN.CommFscode").value(987654))
                .andExpect(jsonPath("$.XFRFUN.CommTaccno").value(87654321))
                .andExpect(jsonPath("$.XFRFUN.CommTavbal").value(175.00))
                .andExpect(jsonPath("$.XFRFUN.CommSuccess").value("Y"));
    }

    @Test
    void returnsNotFoundProblemDetailWhenFromAccountIsMissing() throws Exception {
        when(xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00"))))
                .thenReturn(XfrfunResult.failure("1", "From account number 12345678 was not found."));

        mockMvc.perform(post("/api/v1/xfrfun").contentType(APPLICATION_JSON).content(requestJson("25.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("From account not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsBadRequestProblemDetailForBusinessValidationFailures() throws Exception {
        when(xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("0.00"))))
                .thenReturn(XfrfunResult.failure("4", "Please supply an amount greater than zero."));

        mockMvc.perform(post("/api/v1/xfrfun").contentType(APPLICATION_JSON).content(requestJson("0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid transfer amount"))
                .andExpect(jsonPath("$.failCode").value("4"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/xfrfun").contentType(APPLICATION_JSON).content("{\"XFRFUN\":{\"CommFaccno\":12345678}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    private String requestJson(String amount) {
        return """
                {
                  "XFRFUN": {
                    "CommFaccno": 12345678,
                    "CommFscode": 111111,
                    "CommTaccno": 87654321,
                    "CommTscode": 222222,
                    "CommAmt": %s
                  }
                }
                """.formatted(amount);
    }
}