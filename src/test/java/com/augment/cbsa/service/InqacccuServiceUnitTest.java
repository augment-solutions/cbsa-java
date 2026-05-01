package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.jooq.tables.records.AccountRecord;
import com.augment.cbsa.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.jooq.Cursor;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InqacccuServiceUnitTest {

    @Test
    void rejectsNullRequestWithClearMessage() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");

        assertThatThrownBy(() -> service.inquire(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(accountRepository, inqcustService);
    }

    @Test
    void sentinelCustomerNumbersReturnNotFoundWithoutCallingDependencies() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");

        InqacccuResult zeroResult = service.inquire(new InqacccuRequest(0L));
        InqacccuResult lastResult = service.inquire(new InqacccuRequest(9_999_999_999L));

        assertThat(zeroResult.isNotFoundFailure()).isTrue();
        assertThat(zeroResult.message()).isEqualTo("Customer number 0 was not found.");
        assertThat(lastResult.isNotFoundFailure()).isTrue();
        assertThat(lastResult.message()).isEqualTo("Customer number 9999999999 was not found.");
        verifyNoInteractions(accountRepository, inqcustService);
    }

    @Test
    void returnsNotFoundWhenLinkedCustomerInquiryFails() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");
        when(inqcustService.inquire(new InqcustRequest(10L)))
                .thenReturn(InqcustResult.failure("1", 10L, "Customer number 10 was not found."));

        InqacccuResult result = service.inquire(new InqacccuRequest(10L));

        assertThat(result.isNotFoundFailure()).isTrue();
        assertThat(result.message()).isEqualTo("Customer number 10 was not found.");
        verifyNoInteractions(accountRepository);
    }

    @Test
    void returnsAccountsForExistingCustomerIncludingBlankAccountTypes() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");
        AccountRepository.CustomerAccountsCursor cursor = new AccountRepository.CustomerAccountsCursor(mockCursor());
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer(10L)));
        when(accountRepository.openCustomerAccountsCursor("987654", 10L)).thenReturn(cursor);
        when(accountRepository.fetchNext(cursor))
                .thenReturn(Optional.of(account(1L, "ISA")), Optional.of(account(2L, "")), Optional.empty());

        InqacccuResult result = service.inquire(new InqacccuRequest(10L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.customerFound()).isTrue();
        assertThat(result.accounts()).extracting(AccountDetails::accountNumber).containsExactly(1L, 2L);
        assertThat(result.accounts()).extracting(AccountDetails::accountType).containsExactly("ISA", "");
        verify(accountRepository).close(cursor);
    }

    @Test
    void returnsOpenFailureWhenCursorCannotBeOpened() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer(10L)));
        when(accountRepository.openCustomerAccountsCursor("987654", 10L))
                .thenThrow(new DataAccessException("boom") {
                });

        InqacccuResult result = service.inquire(new InqacccuRequest(10L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        assertThat(result.message()).isEqualTo("INQACCCU failed to open the account cursor.");
    }

    @Test
    void returnsFetchFailureWhenCursorIterationFails() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");
        AccountRepository.CustomerAccountsCursor cursor = new AccountRepository.CustomerAccountsCursor(mockCursor());
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer(10L)));
        when(accountRepository.openCustomerAccountsCursor("987654", 10L)).thenReturn(cursor);
        when(accountRepository.fetchNext(cursor)).thenThrow(new DataAccessException("boom") {
        });

        InqacccuResult result = service.inquire(new InqacccuRequest(10L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("3");
        assertThat(result.message()).isEqualTo("INQACCCU failed while fetching account data.");
        verify(accountRepository).close(cursor);
    }

    @Test
    void closeFailureOverridesEarlierFetchFailure() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqcustService inqcustService = mock(InqcustService.class);
        InqacccuService service = new InqacccuService(accountRepository, inqcustService, "987654");
        AccountRepository.CustomerAccountsCursor cursor = new AccountRepository.CustomerAccountsCursor(mockCursor());
        when(inqcustService.inquire(new InqcustRequest(10L))).thenReturn(InqcustResult.success(customer(10L)));
        when(accountRepository.openCustomerAccountsCursor("987654", 10L)).thenReturn(cursor);
        when(accountRepository.fetchNext(cursor)).thenThrow(new DataAccessException("boom") {
        });
        org.mockito.Mockito.doThrow(new DataAccessException("close boom") {
        }).when(accountRepository).close(cursor);

        InqacccuResult result = service.inquire(new InqacccuRequest(10L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("4");
        assertThat(result.message()).isEqualTo("INQACCCU failed to close the account cursor.");
    }

    @SuppressWarnings("unchecked")
    private Cursor<AccountRecord> mockCursor() {
        return mock(Cursor.class);
    }

    private CustomerDetails customer(long customerNumber) {
        return new CustomerDetails(
                "987654",
                customerNumber,
                "Example Customer",
                "1 Example Road",
                LocalDate.of(1990, 1, 1),
                500,
                LocalDate.of(2025, 1, 1)
        );
    }

    private AccountDetails account(long accountNumber, String accountType) {
        return new AccountDetails(
                "987654",
                10L,
                accountNumber,
                accountType,
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