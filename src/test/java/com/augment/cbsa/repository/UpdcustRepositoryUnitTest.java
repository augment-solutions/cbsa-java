package com.augment.cbsa.repository;

import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the <strong>outer</strong> DataAccessException catch
 * in {@link UpdcustRepository#updateCustomer}: serialization-retry exhaustion
 * escaping {@code CrdbRetry.run(...)} surfaces as {@code XRTY}, while any other
 * DAE is re-thrown so the global handler classifies it as {@code UNEX}.
 *
 * <p>The <strong>inner</strong> PROCTRAN-insert catch (HWPT wrapping vs SQLSTATE
 * 40001 rethrow) is intentionally <em>not</em> covered here: it sits inside the
 * {@code dsl.transactionResult(configuration -> ...)} lambda and is only
 * reachable after stubbing {@code DSL.using(Configuration)} plus the full
 * fluent CUSTOMER/PROCTRAN chains, which is best done as integration coverage.
 * See <a href="https://github.com/augment-solutions/cbsa-java/issues/36">#36</a>
 * for the production fix (Spring Boot's {@code DefaultExceptionTranslatorExecuteListener}
 * substitutes Spring DAEs for jOOQ DAEs, so the inner catches do not fire in
 * production today) and the integration tests that should land alongside it.
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
