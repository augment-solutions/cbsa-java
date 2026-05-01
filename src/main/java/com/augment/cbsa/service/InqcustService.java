package com.augment.cbsa.service;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.CustomerRepository;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.jooq.exception.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InqcustService {

    private static final String ABEND_CODE = "CVR1";
    private static final String BACKEND_FAILURE_CODE = "9";
    private static final String NOT_FOUND_CODE = "1";
    private static final String RANDOM_RETRY_EXHAUSTED_CODE = "R";
    private static final long RANDOM_CUSTOMER_NUMBER = 0L;
    private static final long LAST_CUSTOMER_NUMBER = 9_999_999_999L;
    private static final int RANDOM_RETRY_LIMIT = 1000;

    private final CustomerRepository customerRepository;
    private final String sortcode;

    public InqcustService(CustomerRepository customerRepository, @Value("${cbsa.sortcode}") String sortcode) {
        this.customerRepository = customerRepository;
        this.sortcode = sortcode;
    }

    public InqcustResult inquire(InqcustRequest request) {
        try {
            long customerNumber = request.customerNumber();

            if (customerNumber == RANDOM_CUSTOMER_NUMBER) {
                return findRandomCustomer();
            }

            if (customerNumber == LAST_CUSTOMER_NUMBER) {
                return customerRepository.findLastBySortcode(sortcode)
                        .map(InqcustResult::success)
                        .orElseGet(() -> InqcustResult.failure(
                                BACKEND_FAILURE_CODE,
                                customerNumber,
                                "Unable to determine the last customer."
                        ));
            }

            return customerRepository.findBySortcodeAndCustomerNumber(sortcode, customerNumber)
                    .map(InqcustResult::success)
                    .orElseGet(() -> InqcustResult.failure(
                            NOT_FOUND_CODE,
                            customerNumber,
                            "Customer number %d was not found.".formatted(customerNumber)
                    ));
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(ABEND_CODE, "INQCUST failed to read the customer data.", exception);
        }
    }

    private InqcustResult findRandomCustomer() {
        Optional<CustomerDetails> lastCustomer = customerRepository.findLastBySortcode(sortcode);
        if (lastCustomer.isEmpty() || lastCustomer.get().customerNumber() < 1) {
            return InqcustResult.failure(BACKEND_FAILURE_CODE, RANDOM_CUSTOMER_NUMBER, "Unable to determine a random customer.");
        }

        long highestCustomerNumber = lastCustomer.get().customerNumber();
        for (int attempt = 0; attempt < RANDOM_RETRY_LIMIT; attempt++) {
            long candidate = nextRandomCustomerNumber(highestCustomerNumber);
            Optional<CustomerDetails> customer = customerRepository.findBySortcodeAndCustomerNumber(sortcode, candidate);
            if (customer.isPresent()) {
                return InqcustResult.success(customer.get());
            }
        }

        return InqcustResult.failure(
                RANDOM_RETRY_EXHAUSTED_CODE,
                RANDOM_CUSTOMER_NUMBER,
                "Unable to find a random customer after exhausting retry attempts."
        );
    }

    long nextRandomCustomerNumber(long highestCustomerNumber) {
        return ThreadLocalRandom.current().nextLong(1, highestCustomerNumber + 1);
    }
}