package com.augment.cbsa.service;

import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class CreditAgencyServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private CreditAgencyService creditAgencyService;

    @MockBean
    private CreditAgencyDelayGenerator creditAgencyDelayGenerator;

    @MockBean
    private CreditAgencyDelayExecutor creditAgencyDelayExecutor;

    @MockBean
    private CreditAgencyScoreGenerator creditAgencyScoreGenerator;

    @Test
    void returnsAgencySpecificScoresWithinSpringContext() throws Exception {
        when(creditAgencyDelayGenerator.nextDelay(any())).thenReturn(Duration.ZERO);
        when(creditAgencyScoreGenerator.nextCreditScore(any(), any())).thenAnswer(invocation -> 400 + invocation.getArgument(0, com.augment.cbsa.domain.CreditAgency.class).agencyNumber());

        CrecustRequest request = new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000);

        assertThat(creditAgencyService.requestCreditScore(request, 1).get(1, TimeUnit.SECONDS)).contains(401);
        assertThat(creditAgencyService.requestCreditScore(request, 5).get(1, TimeUnit.SECONDS)).contains(405);
    }
}