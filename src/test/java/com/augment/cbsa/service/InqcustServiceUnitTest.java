package com.augment.cbsa.service;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.repository.CustomerRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InqcustServiceUnitTest {

    @Test
    void randomModeStopsAtRetryLimitAndReturnsRetryExhaustedFailure() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        InqcustService service = new InqcustService(customerRepository, "987654") {
            @Override
            long nextRandomCustomerNumber(long highestCustomerNumber) {
                return 1L;
            }
        };
        when(customerRepository.findLastBySortcode("987654")).thenReturn(Optional.of(customer(2L)));
        when(customerRepository.findBySortcodeAndCustomerNumber("987654", 1L)).thenReturn(Optional.empty());

        InqcustResult result = service.inquire(new InqcustRequest(0L));

        assertThat(result.inquirySuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("R");
        assertThat(result.message()).isEqualTo("Unable to find a random customer after exhausting retry attempts.");
        verify(customerRepository, times(1000)).findBySortcodeAndCustomerNumber("987654", 1L);
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
}