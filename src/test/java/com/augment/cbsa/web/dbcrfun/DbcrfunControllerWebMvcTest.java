package com.augment.cbsa.web.dbcrfun;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DbcrfunOrigin;
import com.augment.cbsa.domain.DbcrfunRequest;
import com.augment.cbsa.domain.DbcrfunResult;
import com.augment.cbsa.error.CbsaExceptionHandler;
import com.augment.cbsa.service.DbcrfunService;
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

@WebMvcTest(DbcrfunController.class)
@Import(CbsaExceptionHandler.class)
class DbcrfunControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DbcrfunService dbcrfunService;

    @Test
    void returnsSuccessfulResponseForHappyPath() throws Exception {
        when(dbcrfunService.process(request(new BigDecimal("25.00"), 496))).thenReturn(DbcrfunResult.success(new AccountDetails(
                "987654",
                10L,
                12345678L,
                "ISA",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("525.00"),
                new BigDecimal("525.00")
        )));

        mockMvc.perform(put("/api/v1/makepayment/dbcr").contentType(APPLICATION_JSON).content(requestJson("25.00", 496)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PAYDBCR.CommAccno").value("12345678"))
                .andExpect(jsonPath("$.PAYDBCR.mSortC").value(987654))
                .andExpect(jsonPath("$.PAYDBCR.CommAvBal").value(525.00))
                .andExpect(jsonPath("$.PAYDBCR.CommActBal").value(525.00))
                .andExpect(jsonPath("$.PAYDBCR.CommOrigin.CommApplid").value("ABCDEFGH"))
                .andExpect(jsonPath("$.PAYDBCR.CommSuccess").value("Y"));
    }

    @Test
    void returnsProblemDetailForNotFoundFailures() throws Exception {
        when(dbcrfunService.process(request(new BigDecimal("25.00"), 496)))
                .thenReturn(DbcrfunResult.failure("1", "Account number 12345678 was not found."));

        mockMvc.perform(put("/api/v1/makepayment/dbcr").contentType(APPLICATION_JSON).content(requestJson("25.00", 496)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.failCode").value("1"));
    }

    @Test
    void returnsProblemDetailForConflictFailures() throws Exception {
        when(dbcrfunService.process(request(new BigDecimal("-25.00"), 496)))
                .thenReturn(DbcrfunResult.failure("3", "Account number 12345678 does not have sufficient available funds."));

        mockMvc.perform(put("/api/v1/makepayment/dbcr").contentType(APPLICATION_JSON).content(requestJson("-25.00", 496)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.failCode").value("3"));
    }

    @Test
    void requestValidationFailuresRemainProblemDetails() throws Exception {
        mockMvc.perform(put("/api/v1/makepayment/dbcr").contentType(APPLICATION_JSON).content(missingAmountJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    private DbcrfunRequest request(BigDecimal amount, int facilityType) {
        return new DbcrfunRequest(12345678L, amount, new DbcrfunOrigin("ABCDEFGH", "12345678", "PAYAPI", "NET00001", facilityType, ""));
    }

    private String requestJson(String amount, int facilityType) {
        return """
                {
                  "PAYDBCR": {
                    "CommAccno": "12345678",
                    "CommAmt": %s,
                    "mSortC": 0,
                    "CommAvBal": 0,
                    "CommActBal": 0,
                    "CommOrigin": {
                      "CommApplid": "ABCDEFGH",
                      "CommUserid": "12345678",
                      "CommFacilityName": "PAYAPI",
                      "CommNetwrkId": "NET00001",
                      "CommFaciltype": %d,
                      "Fill0": ""
                    },
                    "CommSuccess": " ",
                    "CommFailCode": " "
                  }
                }
                """.formatted(amount, facilityType);
    }

    private String missingAmountJson() {
        return """
                {
                  "PAYDBCR": {
                    "CommAccno": "12345678"
                  }
                }
                """;
    }
}