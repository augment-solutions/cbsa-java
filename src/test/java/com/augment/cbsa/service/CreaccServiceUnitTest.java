package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CreaccCommand;
import com.augment.cbsa.domain.CreaccRequest;
import com.augment.cbsa.domain.CreaccResult;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.repository.CreaccRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreaccServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    private CreaccRepository creaccRepository;
    private InqcustService inqcustService;
    private InqacccuService inqacccuService;
    private CreaccService creaccService;

    @BeforeEach
    void setUp() {
        creaccRepository = mock(CreaccRepository.class);
        inqcustService = mock(InqcustService.class);
        inqacccuService = mock(InqacccuService.class);
        creaccService = new CreaccService(
                creaccRepository,
                inqcustService,
                inqacccuService,
                new com.augment.cbsa.config.CbsaProperties("987654"),
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsUnsupportedAccountTypes() {
        CreaccResult result = creaccService.create(request("BONDS"));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("A");
        verifyNoInteractions(inqcustService, inqacccuService, creaccRepository);
    }

    @Test
    void returnsNotFoundWhenLinkedCustomerInquiryFails() {
        when(inqcustService.inquire(new InqcustRequest(10L)))
                .thenReturn(InqcustResult.failure("1", 10L, "Customer number 10 was not found."));

        CreaccResult result = creaccService.create(request("ISA"));

        assertThat(result.isNotFoundFailure()).isTrue();
        assertThat(result.message()).isEqualTo("Customer number 10 was not found.");
        verifyNoInteractions(inqacccuService, creaccRepository);
    }

    @Test
    void returnsCountFailureWhenCustomerAccountCountCannotBeComputed() {
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer()));
        when(inqacccuService.inquire(new InqacccuRequest(10L)))
                .thenReturn(InqacccuResult.failure("3", 10L, true, "INQACCCU failed while fetching account data."));

        CreaccResult result = creaccService.create(request("ISA"));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("9");
        assertThat(result.message()).isEqualTo("INQACCCU failed while fetching account data.");
        verifyNoInteractions(creaccRepository);
    }

    @Test
    void returnsCapacityFailureWhenCustomerAlreadyHasTenAccounts() {
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer()));
        when(inqacccuService.inquire(new InqacccuRequest(10L))).thenReturn(
                InqacccuResult.success(10L, java.util.stream.LongStream.rangeClosed(1, 10)
                        .mapToObj(this::account)
                        .toList())
        );

        CreaccResult result = creaccService.create(request("ISA"));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("8");
        verifyNoInteractions(creaccRepository);
    }

    @Test
    void buildsCreationCommandFromValidatedInput() {
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer()));
        when(inqacccuService.inquire(new InqacccuRequest(10L))).thenReturn(InqacccuResult.success(10L, java.util.List.of()));
        when(creaccRepository.createAccount(any())).thenAnswer(invocation -> {
            CreaccCommand command = invocation.getArgument(0, CreaccCommand.class);
            return CreaccResult.success(new AccountDetails(
                    command.sortcode(),
                    command.customerNumber(),
                    42L,
                    command.accountType(),
                    command.interestRate(),
                    command.opened(),
                    command.overdraftLimit(),
                    command.lastStatementDate(),
                    command.nextStatementDate(),
                    command.availableBalance(),
                    command.actualBalance()
            ));
        });

        CreaccResult result = creaccService.create(request("ISA"));

        assertThat(result.creationSuccess()).isTrue();
        ArgumentCaptor<CreaccCommand> commandCaptor = ArgumentCaptor.forClass(CreaccCommand.class);
        verify(creaccRepository).createAccount(commandCaptor.capture());
        CreaccCommand command = commandCaptor.getValue();
        assertThat(command.sortcode()).isEqualTo("987654");
        assertThat(command.customerNumber()).isEqualTo(10L);
        assertThat(command.accountType()).isEqualTo("ISA");
        assertThat(command.opened()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(command.lastStatementDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(command.nextStatementDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(command.transactionTime().toString()).isEqualTo("10:15:30");
    }

    private CreaccRequest request(String accountType) {
        return new CreaccRequest(10L, accountType, new BigDecimal("1.50"), 250L, new BigDecimal("1500.25"), new BigDecimal("1499.75"));
    }

    private CustomerDetails customer() {
        return new CustomerDetails("987654", 10L, "Example Customer", "1 Example Road", LocalDate.of(1990, 1, 1), 500, LocalDate.of(2025, 1, 1));
    }

    private AccountDetails account(long accountNumber) {
        return new AccountDetails("987654", 10L, accountNumber, "ISA", new BigDecimal("1.50"), LocalDate.of(2024, 1, 2), new BigDecimal("250.00"), LocalDate.of(2024, 2, 3), LocalDate.of(2024, 3, 4), new BigDecimal("1500.25"), new BigDecimal("1499.75"));
    }
}