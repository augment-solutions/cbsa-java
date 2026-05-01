package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.UpdaccRequest;
import com.augment.cbsa.domain.UpdaccResult;
import com.augment.cbsa.repository.UpdaccRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdaccServiceUnitTest {

    @Test
    void rejectsNullRequestWithClearMessage() {
        UpdaccRepository repository = mock(UpdaccRepository.class);
        UpdaccService service = new UpdaccService(repository, "987654");

        assertThatThrownBy(() -> service.update(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsBlankAccountTypeBeforeHittingTheRepository() {
        UpdaccRepository repository = mock(UpdaccRepository.class);
        UpdaccService service = new UpdaccService(repository, "987654");

        UpdaccResult result = service.update(new UpdaccRequest(12345678L, "", new BigDecimal("2.25"), 500L));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        assertThat(result.message()).isEqualTo("Account type must not be blank or start with a space.");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsLeadingSpaceAccountTypeBeforeHittingTheRepository() {
        UpdaccRepository repository = mock(UpdaccRepository.class);
        UpdaccService service = new UpdaccService(repository, "987654");

        UpdaccResult result = service.update(new UpdaccRequest(12345678L, " ISA", new BigDecimal("2.25"), 500L));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("2");
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsAnyNonBlankAccountTypeThatDoesNotStartWithSpace() {
        UpdaccRepository repository = mock(UpdaccRepository.class);
        UpdaccService service = new UpdaccService(repository, "987654");
        UpdaccRequest request = new UpdaccRequest(12345678L, "BROKER", new BigDecimal("2.25"), 500L);
        when(repository.updateAccount("987654", request)).thenReturn(UpdaccResult.success(account("BROKER")));

        UpdaccResult result = service.update(request);

        assertThat(result.updateSuccess()).isTrue();
        assertThat(result.account()).isNotNull();
        assertThat(result.account().accountType()).isEqualTo("BROKER");
    }

    private AccountDetails account() {
        return account("ISA");
    }

    private AccountDetails account(String accountType) {
        return new AccountDetails(
                "987654",
                10L,
                12345678L,
                accountType,
                new BigDecimal("2.25"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("500.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("1500.25"),
                new BigDecimal("1499.75")
        );
    }
}