package com.augment.cbsa.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XfrfunResultTest {

    @Test
    void successFactoryMethodCreatesValidSuccessResult() {
        XfrfunResult result = XfrfunResult.success(
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("200.00")
        );

        assertThat(result.transferSuccess()).isTrue();
        assertThat(result.failCode()).isEqualTo("0");
        assertThat(result.fromAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(result.message()).isNull();
    }

    @Test
    void failureFactoryMethodCreatesValidFailureResult() {
        XfrfunResult result = XfrfunResult.failure("1", "Account not found");

        assertThat(result.transferSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(result.message()).isEqualTo("Account not found");
        assertThat(result.fromAvailableBalance()).isNull();
    }

    @Test
    void rejectsNullFailCode() {
        assertThatThrownBy(() -> new XfrfunResult(true, null, new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("failCode must not be null");
    }

    @Test
    void rejectsSuccessResultWithNullFromAvailableBalance() {
        assertThatThrownBy(() -> new XfrfunResult(true, "0", null, new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Successful results must include the from available balance");
    }

    @Test
    void rejectsSuccessResultWithNullFromActualBalance() {
        assertThatThrownBy(() -> new XfrfunResult(true, "0", new BigDecimal("1"), null, new BigDecimal("1"), new BigDecimal("1"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Successful results must include the from actual balance");
    }

    @Test
    void rejectsSuccessResultWithNullToAvailableBalance() {
        assertThatThrownBy(() -> new XfrfunResult(true, "0", new BigDecimal("1"), new BigDecimal("1"), null, new BigDecimal("1"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Successful results must include the to available balance");
    }

    @Test
    void rejectsSuccessResultWithNullToActualBalance() {
        assertThatThrownBy(() -> new XfrfunResult(true, "0", new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Successful results must include the to actual balance");
    }

    @Test
    void rejectsSuccessResultWithNonBlankMessage() {
        assertThatThrownBy(() -> new XfrfunResult(true, "0", new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), "Error message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Successful results must not include a message");
    }

    @Test
    void rejectsFailureResultWithBalances() {
        assertThatThrownBy(() -> new XfrfunResult(false, "1", new BigDecimal("1"), null, null, null, "Error"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must not include balances");
    }

    @Test
    void rejectsFailureResultWithNullMessage() {
        assertThatThrownBy(() -> new XfrfunResult(false, "1", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must include a non-blank message");
    }

    @Test
    void rejectsFailureResultWithBlankMessage() {
        assertThatThrownBy(() -> new XfrfunResult(false, "1", null, null, null, null, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failure results must include a non-blank message");
    }

    @Test
    void identifiesFromAccountNotFoundFailure() {
        XfrfunResult result = XfrfunResult.failure("1", "From account not found");
        assertThat(result.isFromAccountNotFoundFailure()).isTrue();
        assertThat(result.isToAccountNotFoundFailure()).isFalse();
        assertThat(result.isInvalidAmountFailure()).isFalse();
    }

    @Test
    void identifiesToAccountNotFoundFailure() {
        XfrfunResult result = XfrfunResult.failure("2", "To account not found");
        assertThat(result.isFromAccountNotFoundFailure()).isFalse();
        assertThat(result.isToAccountNotFoundFailure()).isTrue();
        assertThat(result.isInvalidAmountFailure()).isFalse();
    }

    @Test
    void identifiesInvalidAmountFailure() {
        XfrfunResult result = XfrfunResult.failure("4", "Invalid amount");
        assertThat(result.isFromAccountNotFoundFailure()).isFalse();
        assertThat(result.isToAccountNotFoundFailure()).isFalse();
        assertThat(result.isInvalidAmountFailure()).isTrue();
    }

    @Test
    void successResultDoesNotMatchFailurePredicates() {
        XfrfunResult result = XfrfunResult.success(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
        assertThat(result.isFromAccountNotFoundFailure()).isFalse();
        assertThat(result.isToAccountNotFoundFailure()).isFalse();
        assertThat(result.isInvalidAmountFailure()).isFalse();
    }
}
