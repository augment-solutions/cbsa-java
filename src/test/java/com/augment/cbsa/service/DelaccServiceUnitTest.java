package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DelaccRequest;
import com.augment.cbsa.domain.DelaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.DelaccRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DelaccServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    private DelaccRepository delaccRepository;
    private DelaccService delaccService;

    @BeforeEach
    void setUp() {
        delaccRepository = mock(DelaccRepository.class);
        DSLContext dsl = mock(DSLContext.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<DelaccResult> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        delaccService = new DelaccService(
                delaccRepository,
                dsl,
                transactionTemplate,
                new com.augment.cbsa.config.CbsaProperties("987654"),
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsNullRequestWithClearMessage() {
        assertThatThrownBy(() -> delaccService.delete(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(delaccRepository);
    }

    @Test
    void returnsNotFoundWhenAccountDoesNotExist() {
        when(delaccRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.empty());

        DelaccResult result = delaccService.delete(new DelaccRequest(12345678L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Account number 12345678 was not found.");
    }

    @Test
    void returnsDeleteFailureWhenDeleteAffectsNoRows() {
        AccountDetails account = account();
        when(delaccRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.of(account));
        when(delaccRepository.deleteAccount("987654", 12345678L)).thenReturn(0);

        DelaccResult result = delaccService.delete(new DelaccRequest(12345678L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("3");
        verify(delaccRepository).deleteAccount("987654", 12345678L);
    }

    @Test
    void returnsDeleteFailureWhenDeleteRaisesNonRetryableDataAccessException() {
        AccountDetails account = account();
        when(delaccRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.of(account));
        when(delaccRepository.deleteAccount("987654", 12345678L)).thenThrow(new DataAccessException("delete failed") {
        });

        DelaccResult result = delaccService.delete(new DelaccRequest(12345678L));

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("3");
        assertThat(result.message()).isEqualTo("Account number 12345678 could not be deleted.");
    }

    @Test
    void deletesAccountAndWritesAuditTrail() {
        AccountDetails account = account();
        when(delaccRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.of(account));
        when(delaccRepository.deleteAccount("987654", 12345678L)).thenReturn(1);

        DelaccResult result = delaccService.delete(new DelaccRequest(12345678L));

        assertThat(result.deleteSuccess()).isTrue();
        assertThat(result.account()).isEqualTo(account);
        verify(delaccRepository).insertAccountDeletionAudit(
                eq(account),
                eq(1_777_630_530_000L),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30))
        );
    }

    @Test
    void wrapsReadFailuresAsProgramAbends() {
        when(delaccRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L))
                .thenThrow(new DataAccessException("read failed") {
                });

        assertThatThrownBy(() -> delaccService.delete(new DelaccRequest(12345678L)))
                .isInstanceOf(CbsaAbendException.class)
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("HRAC");
    }

    @Test
    void wrapsAuditFailuresAsProgramAbends() {
        AccountDetails account = account();
        when(delaccRepository.findBySortcodeAndAccountNumberForUpdate("987654", 12345678L)).thenReturn(Optional.of(account));
        when(delaccRepository.deleteAccount("987654", 12345678L)).thenReturn(1);
        org.mockito.Mockito.doThrow(new DataAccessException("audit failed") {
        }).when(delaccRepository).insertAccountDeletionAudit(eq(account), anyLong(), any(LocalDate.class), any(LocalTime.class));

        assertThatThrownBy(() -> delaccService.delete(new DelaccRequest(12345678L)))
                .isInstanceOf(CbsaAbendException.class)
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("HWPT");
    }

    private AccountDetails account() {
        return new AccountDetails(
                "987654",
                10L,
                12345678L,
                "ISA",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("1500.25"),
                new BigDecimal("1499.75")
        );
    }
}
