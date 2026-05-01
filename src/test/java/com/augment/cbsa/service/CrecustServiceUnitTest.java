package com.augment.cbsa.service;

import com.augment.cbsa.domain.CrecustCommand;
import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.repository.CrecustRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrecustServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private CrecustRepository crecustRepository;

    @Mock
    private CreditAgencyService creditAgencyService;

    @Mock
    private Random reviewDateRandom;

    private CrecustService crecustService;

    @BeforeEach
    void setUp() {
        crecustService = new CrecustService(crecustRepository, creditAgencyService, "987654", FIXED_CLOCK, reviewDateRandom);
    }

    @Test
    void rejectsInvalidCustomerTitles() {
        CrecustResult result = crecustService.create(new CrecustRequest("John Example", "1 Main Street", 1012000));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("T");
        verifyNoInteractions(creditAgencyService, crecustRepository, reviewDateRandom);
    }

    @Test
    void rejectsDatesEarlierThan1601() {
        CrecustResult result = crecustService.create(new CrecustRequest("Dr Example", "1 Main Street", 1011500));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("O");
        verifyNoInteractions(creditAgencyService, crecustRepository, reviewDateRandom);
    }

    @Test
    void rejectsInvalidCalendarDates() {
        CrecustResult result = crecustService.create(new CrecustRequest("Dr Example", "1 Main Street", 31_02_2000));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("Z");
        verifyNoInteractions(creditAgencyService, crecustRepository, reviewDateRandom);
    }

    @Test
    void rejectsDatesOfBirthMoreThan150YearsAgo() {
        CrecustResult result = crecustService.create(new CrecustRequest("Dr Example", "1 Main Street", 10_01_1800));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("O");
        verifyNoInteractions(creditAgencyService, crecustRepository, reviewDateRandom);
    }

    @Test
    void rejectsFutureDatesOfBirth() {
        CrecustResult result = crecustService.create(new CrecustRequest("Dr Example", "1 Main Street", 10_01_2030));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("Y");
        verifyNoInteractions(crecustRepository, reviewDateRandom);
    }

    @Test
    void returnsCreditFailureWhenNoAgencyResponds() {
        when(creditAgencyService.requestCreditScore(any(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        CrecustResult result = crecustService.create(new CrecustRequest("Dr Example", "1 Main Street", 10_01_2000));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("G");
        verifyNoInteractions(crecustRepository, reviewDateRandom);
    }

    @Test
    void buildsCreationCommandFromValidatedInput() {
        when(creditAgencyService.requestCreditScore(any(), anyInt())).thenAnswer(invocation -> {
            int agencyNumber = invocation.getArgument(1, Integer.class);
            return CompletableFuture.completedFuture(Optional.of(400 + (agencyNumber * 10)));
        });
        when(reviewDateRandom.nextInt(20)).thenReturn(6);
        when(crecustRepository.createCustomer(any())).thenAnswer(invocation -> {
            CrecustCommand command = invocation.getArgument(0, CrecustCommand.class);
            return CrecustResult.success(new CustomerDetails(
                    command.sortcode(),
                    42L,
                    command.name(),
                    command.address(),
                    command.dateOfBirth(),
                    command.creditScore(),
                    command.reviewDate()
            ));
        });

        CrecustResult result = crecustService.create(new CrecustRequest("Dr Alice Example", "1 Main Street", 10_01_2000));

        assertThat(result.creationSuccess()).isTrue();
        assertThat(result.customer()).isNotNull();
        assertThat(result.customer().customerNumber()).isEqualTo(42L);

        ArgumentCaptor<CrecustCommand> commandCaptor = ArgumentCaptor.forClass(CrecustCommand.class);
        verify(crecustRepository).createCustomer(commandCaptor.capture());
        CrecustCommand command = commandCaptor.getValue();
        assertThat(command.sortcode()).isEqualTo("987654");
        assertThat(command.creditScore()).isEqualTo(430);
        assertThat(command.reviewDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(command.transactionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(command.transactionTime().toString()).isEqualTo("10:15:30");
    }
}
