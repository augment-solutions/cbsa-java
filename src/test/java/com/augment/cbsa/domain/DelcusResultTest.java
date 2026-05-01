package com.augment.cbsa.domain;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelcusResultTest {

    @Test
    void successRejectsNullCustomerWithExplicitMessage() {
        assertThatThrownBy(() -> DelcusResult.success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("customer must not be null");
    }

    @Test
    void successRequiresNonNullCustomer() {
        CustomerDetails customer = new CustomerDetails(
                "987654",
                42L,
                "Mr Test User",
                "123 Main St",
                LocalDate.of(1990, 1, 1),
                500,
                LocalDate.of(2026, 6, 1)
        );

        DelcusResult result = DelcusResult.success(customer);

        assertThat(result.deleteSuccess()).isTrue();
        assertThat(result.customer()).isEqualTo(customer);
        assertThat(result.customerNumber()).isEqualTo(42L);
        assertThat(result.failCode()).isEqualTo(" ");
        assertThat(result.message()).isNull();
    }

    @Test
    void failureRejectsNullFailCode() {
        assertThatThrownBy(() -> DelcusResult.failure(null, 1L, "Error message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("failCode must not be null");
    }

    @Test
    void failureRejectsNullMessage() {
        assertThatThrownBy(() -> DelcusResult.failure("1", 1L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message must not be null");
    }

    @Test
    void failureCreatesFailedResultWithCorrectProperties() {
        DelcusResult result = DelcusResult.failure("1", 42L, "Customer not found");

        assertThat(result.deleteSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.customerNumber()).isEqualTo(42L);
        assertThat(result.message()).isEqualTo("Customer not found");
        assertThat(result.customer()).isNull();
    }

    @Test
    void compactConstructorRejectsNullFailCode() {
        assertThatThrownBy(() -> new DelcusResult(true, null, 1L, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("failCode must not be null");
    }

    @Test
    void compactConstructorRejectsSuccessWithoutCustomer() {
        assertThatThrownBy(() -> new DelcusResult(true, " ", 1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Successful results must include a customer");
    }

    @Test
    void compactConstructorRejectsFailureWithCustomer() {
        CustomerDetails customer = new CustomerDetails(
                "987654",
                42L,
                "Mr Test User",
                "123 Main St",
                LocalDate.of(1990, 1, 1),
                500,
                LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> new DelcusResult(false, "1", 42L, customer, "Error"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must not include a customer");
    }

    @Test
    void compactConstructorRejectsFailureWithNullMessage() {
        assertThatThrownBy(() -> new DelcusResult(false, "1", 1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must include a non-blank message");
    }

    @Test
    void compactConstructorRejectsFailureWithBlankMessage() {
        assertThatThrownBy(() -> new DelcusResult(false, "1", 1L, null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must include a non-blank message");
    }

    @Test
    void isNotFoundFailureReturnsTrueForFailCode1() {
        DelcusResult result = DelcusResult.failure("1", 1L, "Not found");

        assertThat(result.isNotFoundFailure()).isTrue();
        assertThat(result.isRandomRetryExhaustedFailure()).isFalse();
    }

    @Test
    void isRandomRetryExhaustedFailureReturnsTrueForFailCodeR() {
        DelcusResult result = DelcusResult.failure("R", 1L, "Retry exhausted");

        assertThat(result.isNotFoundFailure()).isFalse();
        assertThat(result.isRandomRetryExhaustedFailure()).isTrue();
    }

    @Test
    void successResultReturnsFalseForAllFailurePredicates() {
        CustomerDetails customer = new CustomerDetails(
                "987654",
                42L,
                "Mr Test User",
                "123 Main St",
                LocalDate.of(1990, 1, 1),
                500,
                LocalDate.of(2026, 6, 1)
        );
        DelcusResult result = DelcusResult.success(customer);

        assertThat(result.isNotFoundFailure()).isFalse();
        assertThat(result.isRandomRetryExhaustedFailure()).isFalse();
    }

    @Test
    void otherFailureCodesReturnFalseForSpecificPredicates() {
        DelcusResult result = DelcusResult.failure("3", 1L, "Other error");

        assertThat(result.isNotFoundFailure()).isFalse();
        assertThat(result.isRandomRetryExhaustedFailure()).isFalse();
    }
}
