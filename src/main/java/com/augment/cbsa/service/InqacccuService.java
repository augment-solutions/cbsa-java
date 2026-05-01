package com.augment.cbsa.service;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.repository.AccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InqacccuService {

    private static final String NOT_FOUND_CODE = "1";
    private static final String OPEN_FAILURE_CODE = "2";
    private static final String FETCH_FAILURE_CODE = "3";
    private static final String CLOSE_FAILURE_CODE = "4";
    private static final long ZERO_CUSTOMER_NUMBER = 0L;
    private static final long LAST_CUSTOMER_NUMBER = 9_999_999_999L;

    private final AccountRepository accountRepository;
    private final InqcustService inqcustService;
    private final String sortcode;

    public InqacccuService(
            AccountRepository accountRepository,
            InqcustService inqcustService,
            CbsaProperties cbsaProperties
    ) {
        this.accountRepository = accountRepository;
        this.inqcustService = inqcustService;
        this.sortcode = Objects.requireNonNull(cbsaProperties, "cbsaProperties must not be null").sortcode();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public InqacccuResult inquire(InqacccuRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        long customerNumber = request.customerNumber();
        if (customerNumber == ZERO_CUSTOMER_NUMBER || customerNumber == LAST_CUSTOMER_NUMBER) {
            return customerNotFound(customerNumber);
        }

        InqcustResult customerResult = inqcustService.inquire(new InqcustRequest(customerNumber));
        if (!customerResult.inquirySuccess()) {
            return customerNotFound(customerNumber);
        }

        // The customer was confirmed to exist via inqcustService above; cursor
        // open/fetch/close failures are downstream of that confirmation, so the
        // customerFound flag stays true on those failures.
        AccountRepository.CustomerAccountsCursor cursor;
        try {
            cursor = accountRepository.openCustomerAccountsCursor(sortcode, customerNumber);
        } catch (DataAccessException exception) {
            return InqacccuResult.failure(
                    OPEN_FAILURE_CODE,
                    customerNumber,
                    true,
                    "INQACCCU failed to open the account cursor."
            );
        }

        List<AccountDetails> accounts = new ArrayList<>();
        InqacccuResult pendingFailure = null;
        try {
            try {
                while (true) {
                    var nextAccount = accountRepository.fetchNext(cursor);
                    if (nextAccount.isEmpty()) {
                        break;
                    }
                    accounts.add(nextAccount.get());
                }
            } catch (DataAccessException exception) {
                pendingFailure = InqacccuResult.failure(
                        FETCH_FAILURE_CODE,
                        customerNumber,
                        true,
                        "INQACCCU failed while fetching account data."
                );
            }
        } finally {
            try {
                accountRepository.close(cursor);
            } catch (DataAccessException exception) {
                pendingFailure = InqacccuResult.failure(
                        CLOSE_FAILURE_CODE,
                        customerNumber,
                        true,
                        "INQACCCU failed to close the account cursor."
                );
            }
        }

        if (pendingFailure != null) {
            return pendingFailure;
        }

        return InqacccuResult.success(customerNumber, accounts);
    }

    private InqacccuResult customerNotFound(long customerNumber) {
        return InqacccuResult.failure(
                NOT_FOUND_CODE,
                customerNumber,
                false,
                "Customer number %d was not found.".formatted(customerNumber)
        );
    }
}