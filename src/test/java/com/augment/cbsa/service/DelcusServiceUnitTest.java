package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.DelcusRequest;
import com.augment.cbsa.domain.DelcusResult;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.DelcusRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DelcusServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    private InqcustService inqcustService;
    private InqacccuService inqacccuService;
    private DelcusRepository delcusRepository;
    private DelcusService delcusService;

    @BeforeEach
    void setUp() {
        inqcustService = mock(InqcustService.class);
        inqacccuService = mock(InqacccuService.class);
        delcusRepository = mock(DelcusRepository.class);
        DSLContext dsl = mock(DSLContext.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<DelcusResult> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        delcusService = new DelcusService(
                inqcustService,
                inqacccuService,
                delcusRepository,
                dsl,
                transactionTemplate,
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsNullRequestWithClearMessage() {
        assertThatThrownBy(() -> delcusService.delete(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(inqcustService, inqacccuService, delcusRepository);
    }

    @Test
    void returnsCustomerInquiryFailureWithoutTouchingDeletionFlow() {
        when(inqcustService.inquire(new InqcustRequest(1L)))
                .thenReturn(InqcustResult.failure("1", 1L, "Customer number 1 was not found."));

        DelcusResult result = delcusService.delete(new DelcusRequest(1L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        verifyNoInteractions(inqacccuService, delcusRepository);
    }

    @Test
    void rejectsZeroCustomerNumberWithoutInvokingTheRandomCustomerPath() {
        DelcusResult result = delcusService.delete(new DelcusRequest(0L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        verifyNoInteractions(inqcustService, inqacccuService, delcusRepository);
    }

    @Test
    void deletesResolvedCustomerAndSkipsAccountAuditWhenTheRowIsAlreadyGone() {
        CustomerDetails customer = customer(42L);
        AccountDetails firstAccount = account(42L, 100L, "ISA", new BigDecimal("1499.75"));
        AccountDetails secondAccount = account(42L, 200L, "SAVINGS", new BigDecimal("500.00"));

        when(inqcustService.inquire(new InqcustRequest(42L))).thenReturn(InqcustResult.success(customer));
        when(inqacccuService.inquire(new InqacccuRequest(42L))).thenReturn(InqacccuResult.success(42L, java.util.List.of(firstAccount, secondAccount)));
        when(delcusRepository.deleteAccount("987654", 100L)).thenReturn(1);
        when(delcusRepository.deleteAccount("987654", 200L)).thenReturn(0);
        when(delcusRepository.deleteCustomer("987654", 42L)).thenReturn(1);

        DelcusResult result = delcusService.delete(new DelcusRequest(42L));

        assertThat(result.deleteSuccess()).isTrue();
        assertThat(result.customer()).isEqualTo(customer);
        verify(inqacccuService).inquire(new InqacccuRequest(42L));
        verify(delcusRepository).insertAccountDeletionAudit(
                eq(firstAccount),
                eq(1_777_630_530_000L),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30))
        );
        verify(delcusRepository).insertCustomerDeletionAudit(
                eq(customer),
                eq(1_777_630_530_000L),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30))
        );
    }

    @Test
    void abendsWhenAccountInquiryFailsAfterCustomerLookup() {
        CustomerDetails customer = customer(42L);
        when(inqcustService.inquire(new InqcustRequest(42L))).thenReturn(InqcustResult.success(customer));
        when(inqacccuService.inquire(new InqacccuRequest(42L)))
                .thenReturn(InqacccuResult.failure("3", 42L, true, "INQACCCU failed while fetching account data."));

        assertThatThrownBy(() -> delcusService.delete(new DelcusRequest(42L)))
                .isInstanceOf(CbsaAbendException.class)
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("WPV6");
        verifyNoInteractions(delcusRepository);
    }

    private CustomerDetails customer(long customerNumber) {
        return new CustomerDetails(
                "987654",
                customerNumber,
                "Mr Alice Example",
                "1 Main Street",
                LocalDate.of(2000, 1, 10),
                430,
                LocalDate.of(2026, 5, 8)
        );
    }

    private AccountDetails account(long customerNumber, long accountNumber, String accountType, BigDecimal actualBalance) {
        return new AccountDetails(
                "987654",
                customerNumber,
                accountNumber,
                accountType,
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                actualBalance,
                actualBalance
        );
    }
}
