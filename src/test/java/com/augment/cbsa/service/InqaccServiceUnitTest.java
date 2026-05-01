package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqaccRequest;
import com.augment.cbsa.domain.InqaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InqaccServiceUnitTest {

    @Test
    void rejectsNullRequestWithClearMessage() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));

        assertThatThrownBy(() -> service.inquire(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(accountRepository);
    }

    @Test
    void returnsAccountForExactAccountNumber() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findBySortcodeAndAccountNumber("987654", 12345678L))
                .thenReturn(Optional.of(account(12345678L, "ISA")));

        InqaccResult result = service.inquire(new InqaccRequest(12345678L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.failCode()).isEqualTo("0");
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountNumber()).isEqualTo(12345678L);
    }

    @Test
    void returnsNotFoundWhenExactAccountDoesNotExist() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findBySortcodeAndAccountNumber("987654", 12345678L)).thenReturn(Optional.empty());

        InqaccResult result = service.inquire(new InqaccRequest(12345678L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Account number 12345678 was not found.");
    }

    @Test
    void returnsBlankAccountTypeAsSuccess() {
        // COBOL INQACC.cbl does not filter rows by account_type; blank-type
        // accounts are returned as-is.
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findBySortcodeAndAccountNumber("987654", 12345678L))
                .thenReturn(Optional.of(account(12345678L, " ")));

        InqaccResult result = service.inquire(new InqaccRequest(12345678L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.failCode()).isEqualTo("0");
        assertThat(result.account().accountType()).isEqualTo(" ");
    }

    @Test
    void lastAccountModeReturnsHighestAccount() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findLastBySortcode("987654")).thenReturn(Optional.of(account(99999998L, "SAVINGS")));

        InqaccResult result = service.inquire(new InqaccRequest(99_999_999L));

        assertThat(result.inquirySuccess()).isTrue();
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountNumber()).isEqualTo(99_999_998L);
    }

    @Test
    void lastAccountModeReturnsNotFoundWhenNoAccountsExist() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findLastBySortcode("987654")).thenReturn(Optional.empty());

        InqaccResult result = service.inquire(new InqaccRequest(99_999_999L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("No accounts exist.");
    }

    @Test
    void wrapsExactLookupDataAccessFailuresInHracAbend() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findBySortcodeAndAccountNumber("987654", 12345678L))
                .thenThrow(new DataAccessException("boom") {
                });

        assertThatThrownBy(() -> service.inquire(new InqaccRequest(12345678L)))
                .isInstanceOf(CbsaAbendException.class)
                .hasMessage("INQACC failed to read the account data.")
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("HRAC");
    }

    @Test
    void wrapsLastLookupDataAccessFailuresInHncsAbend() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InqaccService service = new InqaccService(accountRepository, new com.augment.cbsa.config.CbsaProperties("987654"));
        when(accountRepository.findLastBySortcode("987654"))
                .thenThrow(new DataAccessException("boom") {
                });

        assertThatThrownBy(() -> service.inquire(new InqaccRequest(99_999_999L)))
                .isInstanceOf(CbsaAbendException.class)
                .hasMessage("INQACC failed to read the account data.")
                .extracting(exception -> ((CbsaAbendException) exception).getAbendCode())
                .isEqualTo("HNCS");
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