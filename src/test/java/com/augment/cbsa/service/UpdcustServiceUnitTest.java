package com.augment.cbsa.service;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.repository.UpdcustRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdcustServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    private UpdcustRepository updcustRepository;
    private UpdcustService updcustService;

    @BeforeEach
    void setUp() {
        updcustRepository = mock(UpdcustRepository.class);
        updcustService = new UpdcustService(
                updcustRepository,
                new com.augment.cbsa.config.CbsaProperties("987654"),
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsInvalidTitlesBeforeTouchingTheRepository() {
        UpdcustResult result = updcustService.update(request("Reverend Alice Example", "1 Main Street"));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("T");
        verifyNoInteractions(updcustRepository);
    }

    @Test
    void forwardsValidatedUpdatesWithDeterministicAuditTimestamps() {
        when(updcustRepository.updateCustomer(any(), any(), any(Long.class), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(UpdcustResult.success(customer("Mrs Alice Example", "1 Main Street")));

        UpdcustResult result = updcustService.update(request("Mrs Alice Example", "1 Main Street"));

        assertThat(result.updateSuccess()).isTrue();
        verify(updcustRepository).updateCustomer(
                eq("987654"),
                eq(request("Mrs Alice Example", "1 Main Street")),
                eq(1_777_630_530_000L),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30))
        );
    }

    @Test
    void acceptsLeadingSpaceNamesBecauseCobolTreatsThemAsBlankTitles() {
        when(updcustRepository.updateCustomer(any(), any(), any(Long.class), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(UpdcustResult.failure("4", "Customer name and address must not both be blank."));

        UpdcustResult result = updcustService.update(request(" Alice Example", " "));

        assertThat(result.failCode()).isEqualTo("4");
        verify(updcustRepository).updateCustomer(eq("987654"), eq(request(" Alice Example", " ")), any(Long.class), any(LocalDate.class), any(LocalTime.class));
    }

    private UpdcustRequest request(String name, String address) {
        return new UpdcustRequest(1L, name, address, 10_01_2000, 430, 8_052_026);
    }

    private CustomerDetails customer(String name, String address) {
        return new CustomerDetails("987654", 1L, name, address, LocalDate.of(2000, 1, 10), 430, LocalDate.of(2026, 5, 8));
    }
}
