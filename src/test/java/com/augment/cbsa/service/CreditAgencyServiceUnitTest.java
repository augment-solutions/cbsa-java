package com.augment.cbsa.service;

import com.augment.cbsa.domain.CrecustRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditAgencyServiceUnitTest {

    private static final CrecustRequest REQUEST = new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000);

    @Test
    void mapsEveryAgencyThroughInjectedDelayAndScoreComponents() {
        List<Duration> observedDelays = new ArrayList<>();
        CreditAgencyService service = new CreditAgencyService(
                agency -> Duration.ofMillis(agency.agencyNumber() * 10L),
                observedDelays::add,
                (agency, request) -> 300 + agency.agencyNumber()
        );

        for (int agencyNumber = 1; agencyNumber <= 5; agencyNumber++) {
            assertThat(service.requestCreditScore(REQUEST, agencyNumber).join())
                    .contains(300 + agencyNumber);
        }

        assertThat(observedDelays).containsExactly(
                Duration.ofMillis(10),
                Duration.ofMillis(20),
                Duration.ofMillis(30),
                Duration.ofMillis(40),
                Duration.ofMillis(50)
        );
    }

    @Test
    void rejectsUnknownAgencyNumbers() {
        CreditAgencyService service = new CreditAgencyService(agency -> Duration.ZERO, duration -> { }, (agency, request) -> 500);

        assertThatThrownBy(() -> service.requestCreditScore(REQUEST, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported credit agency number");
    }

    @Test
    void rejectsScoresOutsideCobolRange() {
        CreditAgencyService service = new CreditAgencyService(agency -> Duration.ZERO, duration -> { }, (agency, request) -> 1_000);

        assertThatThrownBy(() -> service.requestCreditScore(REQUEST, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 999");
    }
}