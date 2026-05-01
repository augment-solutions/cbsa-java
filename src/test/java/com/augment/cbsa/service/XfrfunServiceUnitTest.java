package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.XfrfunRequest;
import com.augment.cbsa.domain.XfrfunResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.XfrfunRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XfrfunServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    private XfrfunRepository xfrfunRepository;
    private XfrfunService xfrfunService;

    @BeforeEach
    void setUp() {
        xfrfunRepository = mock(XfrfunRepository.class);
        DSLContext dsl = mock(DSLContext.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<XfrfunResult> callback = invocation.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        xfrfunService = new XfrfunService(
                xfrfunRepository,
                dsl,
                transactionTemplate,
                new com.augment.cbsa.config.CbsaProperties("987654"),
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsNullRequestWithClearMessage() {
        assertThatThrownBy(() -> xfrfunService.transfer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(xfrfunRepository);
    }

    @Test
    void returnsValidationFailureWhenAmountIsZeroOrNegative() {
        XfrfunResult result = xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, BigDecimal.ZERO));

        assertThat(result.transferSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("4");
        assertThat(result.message()).isEqualTo("Please supply an amount greater than zero.");
        verifyNoInteractions(xfrfunRepository);
    }

    @Test
    void abendsWhenTransferTargetsTheSameAccount() {
        assertThatThrownBy(() -> xfrfunService.transfer(new XfrfunRequest(12345678L, 12345678L, new BigDecimal("25.00"))))
                .isInstanceOf(CbsaAbendException.class)
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("SAME");
        verifyNoInteractions(xfrfunRepository);
    }

    @Test
    void returnsFromAccountNotFoundWhenDebitAccountDoesNotExist() {
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.empty());

        XfrfunResult result = xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00")));

        assertThat(result.transferSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("From account number 12345678 was not found.");
    }

    @Test
    void returnsToAccountNotFoundWhenCreditAccountDoesNotExist() {
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.of(account(12345678L, "100.00")));
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 87654321L)).thenReturn(Optional.empty());

        XfrfunResult result = xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00")));

        assertThat(result.transferSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        assertThat(result.message()).isEqualTo("To account number 87654321 was not found.");
    }

    @Test
    void locksLowerAccountFirstAndWritesTransferAuditOnSuccess() {
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 11111111L)).thenReturn(Optional.of(account(11111111L, "50.00")));
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 22222222L)).thenReturn(Optional.of(account(22222222L, "10.00")));
        when(xfrfunRepository.updateBalances("987654", 22222222L, new BigDecimal("-15.00"), new BigDecimal("-15.00"))).thenReturn(1);
        when(xfrfunRepository.updateBalances("987654", 11111111L, new BigDecimal("75.00"), new BigDecimal("75.00"))).thenReturn(1);

        XfrfunResult result = xfrfunService.transfer(new XfrfunRequest(22222222L, 11111111L, new BigDecimal("25.00")));

        assertThat(result.transferSuccess()).isTrue();
        assertThat(result.fromAvailableBalance()).isEqualByComparingTo("-15.00");
        assertThat(result.toAvailableBalance()).isEqualByComparingTo("75.00");

        InOrder inOrder = inOrder(xfrfunRepository);
        inOrder.verify(xfrfunRepository).findBySortcodeAndAccountNumberForUpdate("987654", 11111111L);
        inOrder.verify(xfrfunRepository).findBySortcodeAndAccountNumberForUpdate("987654", 22222222L);
        inOrder.verify(xfrfunRepository).updateBalances("987654", 22222222L, new BigDecimal("-15.00"), new BigDecimal("-15.00"));
        inOrder.verify(xfrfunRepository).updateBalances("987654", 11111111L, new BigDecimal("75.00"), new BigDecimal("75.00"));
        verify(xfrfunRepository).insertTransferAudit(
                eq("987654"),
                eq(22222222L),
                eq("987654"),
                eq(11111111L),
                anyLong(),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30)),
                eq(new BigDecimal("25.00"))
        );
    }

    @Test
    void wrapsAuditFailuresInWpcdAbend() {
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.of(account(12345678L, "100.00")));
        when(xfrfunRepository.findBySortcodeAndAccountNumberForUpdate("987654", 87654321L)).thenReturn(Optional.of(account(87654321L, "10.00")));
        when(xfrfunRepository.updateBalances("987654", 12345678L, new BigDecimal("75.00"), new BigDecimal("75.00"))).thenReturn(1);
        when(xfrfunRepository.updateBalances("987654", 87654321L, new BigDecimal("35.00"), new BigDecimal("35.00"))).thenReturn(1);
        org.mockito.Mockito.doThrow(new DataAccessException("audit failed") {
        }).when(xfrfunRepository).insertTransferAudit(eq("987654"), eq(12345678L), eq("987654"), eq(87654321L), anyLong(), any(LocalDate.class), any(LocalTime.class), eq(new BigDecimal("25.00")));

        assertThatThrownBy(() -> xfrfunService.transfer(new XfrfunRequest(12345678L, 87654321L, new BigDecimal("25.00"))))
                .isInstanceOf(CbsaAbendException.class)
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("WPCD");
    }

    private AccountDetails account(long accountNumber, String balance) {
        return new AccountDetails(
                "987654",
                10L,
                accountNumber,
                "ISA",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal(balance),
                new BigDecimal(balance)
        );
    }
}