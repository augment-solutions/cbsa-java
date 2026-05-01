package com.augment.cbsa.repository;

import com.augment.cbsa.domain.CrecustCommand;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.error.CbsaAbendException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the outer DataAccessException catch in
 * {@link CrecustRepository#createCustomer(CrecustCommand)}: serialization-retry
 * exhaustion surfaces as {@code XRTY}, while any other DAE escaping
 * {@code CrdbRetry.run(...)} is re-thrown so the global handler can classify
 * it as {@code UNEX}. PROCTRAN-specific failures are covered separately by
 * the inner catches in the same method.
 */
@ExtendWith(MockitoExtension.class)
class CrecustRepositoryUnitTest {

    private static final CrecustCommand COMMAND = new CrecustCommand(
            "987654",
            "Dr Alice Example",
            "1 Main Street",
            LocalDate.of(2000, 1, 10),
            430,
            LocalDate.of(2026, 5, 8),
            1234567890L,
            LocalDate.of(2026, 5, 1),
            LocalTime.of(10, 15, 30)
    );

    @Mock
    private DSLContext dsl;

    private CrecustRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CrecustRepository(dsl);
    }

    @Test
    @SuppressWarnings("unchecked")
    void serializationRetryExhaustionAbendsXrty() {
        SQLException sqle = new SQLException("Serialization failure", "40001");
        DataAccessException dae = new DataAccessException("wrapped", sqle) { };
        when(dsl.transactionResult((TransactionalCallable<CrecustResult>) any())).thenThrow(dae);

        assertThatThrownBy(() -> repository.createCustomer(COMMAND))
                .isInstanceOf(CbsaAbendException.class)
                .satisfies(thrown -> {
                    CbsaAbendException abend = (CbsaAbendException) thrown;
                    assertThat(abend.getAbendCode()).isEqualTo("XRTY");
                    assertThat(abend.getMessage())
                            .isEqualTo("CRECUST aborted after exhausting Cockroach serialization retries.");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonSerializationDataAccessExceptionIsRethrown() {
        DataAccessException dae = new DataAccessException("non-retryable") { };
        when(dsl.transactionResult((TransactionalCallable<CrecustResult>) any())).thenThrow(dae);

        assertThatThrownBy(() -> repository.createCustomer(COMMAND))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("non-retryable");
    }
}
