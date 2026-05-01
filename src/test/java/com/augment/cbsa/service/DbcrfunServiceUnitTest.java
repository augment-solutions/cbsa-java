package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.DbcrfunOrigin;
import com.augment.cbsa.domain.DbcrfunRequest;
import com.augment.cbsa.domain.DbcrfunResult;
import com.augment.cbsa.repository.DbcrfunRepository;
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
import org.springframework.transaction.TransactionStatus;
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

class DbcrfunServiceUnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-01T10:15:30Z"), ZoneOffset.UTC);

    private DbcrfunRepository dbcrfunRepository;
    private DbcrfunService dbcrfunService;

    @BeforeEach
    void setUp() {
        dbcrfunRepository = mock(DbcrfunRepository.class);
        DSLContext dsl = mock(DSLContext.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<DbcrfunResult> callback = invocation.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        dbcrfunService = new DbcrfunService(
                dbcrfunRepository,
                dsl,
                transactionTemplate,
                new com.augment.cbsa.config.CbsaProperties("987654"),
                FIXED_CLOCK
        );
    }

    @Test
    void rejectsNullRequestWithClearMessage() {
        assertThatThrownBy(() -> dbcrfunService.process(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(dbcrfunRepository);
    }

    @Test
    void returnsNotFoundWhenAccountDoesNotExist() {
        when(dbcrfunRepository.lockAccount("987654", 12345678L)).thenReturn(Optional.empty());

        DbcrfunResult result = dbcrfunService.process(request(new BigDecimal("25.00"), 496));

        assertThat(result.paymentSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Account number 12345678 was not found.");
    }

    @Test
    void rejectsPaymentFacilityRequestsForMortgageAccounts() {
        when(dbcrfunRepository.lockAccount("987654", 12345678L)).thenReturn(Optional.of(account("MORTGAGE", "500.00")));

        DbcrfunResult result = dbcrfunService.process(request(new BigDecimal("25.00"), 496));

        assertThat(result.paymentSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("4");
        assertThat(result.message()).isEqualTo("Payments are not supported for account type MORTGAGE.");
    }

    @Test
    void rejectsPaymentDebitsThatWouldOverdrawAvailableFunds() {
        when(dbcrfunRepository.lockAccount("987654", 12345678L)).thenReturn(Optional.of(account("ISA", "10.00")));

        DbcrfunResult result = dbcrfunService.process(request(new BigDecimal("-25.00"), 496));

        assertThat(result.paymentSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("3");
        assertThat(result.message()).isEqualTo("Account number 12345678 does not have sufficient available funds.");
    }

    @Test
    void writesPaymentCreditAuditRowsUsingTheOriginHeader() {
        when(dbcrfunRepository.lockAccount("987654", 12345678L)).thenReturn(Optional.of(account("ISA", "100.00")));
        when(dbcrfunRepository.updateBalances("987654", 12345678L, new BigDecimal("125.00"), new BigDecimal("125.00")))
                .thenReturn(1);

        DbcrfunResult result = dbcrfunService.process(request(new BigDecimal("25.00"), 496));

        assertThat(result.paymentSuccess()).isTrue();
        assertThat(result.account().availableBalance()).isEqualByComparingTo("125.00");
        verify(dbcrfunRepository).insertProcTran(
                eq("987654"),
                anyLong(),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30)),
                eq("PCR"),
                eq("ABCDEFGH123456"),
                eq(new BigDecimal("25.00"))
        );
    }

    @Test
    void allowsTellerDebitsToOverdrawAndWritesCounterWithdrawalAudit() {
        when(dbcrfunRepository.lockAccount("987654", 12345678L)).thenReturn(Optional.of(account("ISA", "10.00")));
        when(dbcrfunRepository.updateBalances("987654", 12345678L, new BigDecimal("-15.00"), new BigDecimal("-15.00")))
                .thenReturn(1);

        DbcrfunResult result = dbcrfunService.process(request(new BigDecimal("-25.00"), 0));

        assertThat(result.paymentSuccess()).isTrue();
        assertThat(result.account().availableBalance()).isEqualByComparingTo("-15.00");
        verify(dbcrfunRepository).insertProcTran(
                eq("987654"),
                anyLong(),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalTime.of(10, 15, 30)),
                eq("DEB"),
                eq("COUNTER WTHDRW"),
                eq(new BigDecimal("-25.00"))
        );
    }

    @Test
    void returnsGenericFailureWhenPersistenceThrowsDataAccessException() {
        when(dbcrfunRepository.lockAccount("987654", 12345678L)).thenThrow(new DataAccessException("boom") {
        });

        DbcrfunResult result = dbcrfunService.process(request(new BigDecimal("25.00"), 496));

        assertThat(result.paymentSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        assertThat(result.message()).isEqualTo("DBCRFUN failed to update the account data.");
    }

    private DbcrfunRequest request(BigDecimal amount, int facilityType) {
        return new DbcrfunRequest(12345678L, amount, new DbcrfunOrigin("ABCDEFGH", "12345678", "PAYAPI", "NET00001", facilityType, ""));
    }

    private AccountDetails account(String accountType, String balance) {
        return new AccountDetails(
                "987654",
                10L,
                12345678L,
                accountType,
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