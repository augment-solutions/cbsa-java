package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqaccRequest;
import com.augment.cbsa.domain.InqaccResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.AccountRepository;
import java.util.Objects;
import org.jooq.exception.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InqaccService {

    private static final String EXACT_LOOKUP_ABEND_CODE = "HRAC";
    private static final String LAST_LOOKUP_ABEND_CODE = "HNCS";
    private static final String NO_ACCOUNTS_EXIST_MESSAGE = "No accounts exist.";
    private static final String NOT_FOUND_CODE = "1";
    private static final long LAST_ACCOUNT_NUMBER = 99_999_999L;

    private final AccountRepository accountRepository;
    private final String sortcode;

    public InqaccService(AccountRepository accountRepository, @Value("${cbsa.sortcode}") String sortcode) {
        this.accountRepository = accountRepository;
        this.sortcode = sortcode;
    }

    public InqaccResult inquire(InqaccRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        long accountNumber = request.accountNumber();

        try {
            if (accountNumber == LAST_ACCOUNT_NUMBER) {
                return accountRepository.findLastBySortcode(sortcode)
                        .filter(this::isUsableAccount)
                        .map(InqaccResult::success)
                        .orElseGet(() -> InqaccResult.failure(
                                NOT_FOUND_CODE,
                                accountNumber,
                                NO_ACCOUNTS_EXIST_MESSAGE
                        ));
            }

            return accountRepository.findBySortcodeAndAccountNumber(sortcode, accountNumber)
                    .filter(this::isUsableAccount)
                    .map(InqaccResult::success)
                    .orElseGet(() -> InqaccResult.failure(
                            NOT_FOUND_CODE,
                            accountNumber,
                            "Account number %d was not found.".formatted(accountNumber)
                    ));
        } catch (DataAccessException exception) {
            String abendCode = accountNumber == LAST_ACCOUNT_NUMBER ? LAST_LOOKUP_ABEND_CODE : EXACT_LOOKUP_ABEND_CODE;
            throw new CbsaAbendException(abendCode, "INQACC failed to read the account data.", exception);
        }
    }

    private boolean isUsableAccount(AccountDetails account) {
        return !account.accountType().isBlank();
    }
}