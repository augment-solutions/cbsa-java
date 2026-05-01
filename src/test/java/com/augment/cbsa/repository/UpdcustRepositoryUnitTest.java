package com.augment.cbsa.repository;

import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
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
 * {@link UpdcustRepository#updateCustomer}: serialization-retry exhaustion
 * surfaces as {@code XRTY}, while any other DAE escaping
 * {@code CrdbRetry.run(...)} is re-thrown so the global handler can classify
 * it as {@code UNEX}. PROCTRAN-specific failures are covered separately by
 * the inner catch in the same method.
 */
@ExtendWith(MockitoExtension.class)
class UpdcustRepositoryUnitTest {

    private static final UpdcustRequest REQUEST = new UpdcustRequest(
            1L,
            "Mrs Alice Example",
            "1 Main Street",
            10_01_2000,
            430,
            8_05_2026
    );
    private static final String SORTCODE = "987654";
    private static final long TRANSACTION_REFERENCE = 1234567890L;
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 5, 1);
    private static final LocalTime TRANSACTION_TIME = LocalTime.of(10, 15, 30);

    @Mock
    private DSLContext dsl;

    private UpdcustRepository repository;

    @BeforeEach
    void setUp() {
        repository = new UpdcustRepository(dsl);
    }

    @Test
    @SuppressWarnings("unchecked")
    void serializationRetryExhaustionAbendsXrty() {
        SQLException sqle = new SQLException("Serialization failure", "40001");
        DataAccessException dae = new DataAccessException("wrapped", sqle) { };
        when(dsl.transactionResult((TransactionalCallable<UpdcustResult>) any())).thenThrow(dae);

        assertThatThrownBy(() -> repository.updateCustomer(
                SORTCODE, REQUEST, TRANSACTION_REFERENCE, TRANSACTION_DATE, TRANSACTION_TIME))
                .isInstanceOf(CbsaAbendException.class)
                .satisfies(thrown -> {
                    CbsaAbendException abend = (CbsaAbendException) thrown;
                    assertThat(abend.getAbendCode()).isEqualTo("XRTY");
                    assertThat(abend.getMessage())
                            .isEqualTo("UPDCUST aborted after exhausting Cockroach serialization retries.");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonSerializationDataAccessExceptionIsRethrown() {
        DataAccessException dae = new DataAccessException("non-retryable") { };
        when(dsl.transactionResult((TransactionalCallable<UpdcustResult>) any())).thenThrow(dae);

        assertThatThrownBy(() -> repository.updateCustomer(
                SORTCODE, REQUEST, TRANSACTION_REFERENCE, TRANSACTION_DATE, TRANSACTION_TIME))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("non-retryable");
    }
}
