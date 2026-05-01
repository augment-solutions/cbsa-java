package com.augment.cbsa.repository;

import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.SQLDialect;
import org.jooq.TransactionalCallable;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;

import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the DataAccessException catches in
 * {@link UpdcustRepository#updateCustomer}:
 *
 * <ul>
 *   <li>The <strong>outer</strong> catch turns serialization-retry exhaustion
 *       escaping {@code CrdbRetry.run(...)} into {@code XRTY}, and re-throws
 *       any other DAE so the global handler classifies it as {@code UNEX}.
 *   <li>The <strong>inner</strong> PROCTRAN-insert catch wraps non-retryable
 *       DAEs as {@code CbsaAbendException("HWPT")} and re-throws SQLSTATE
 *       {@code 40001} DAEs unchanged so {@code CrdbRetry} can retry. The
 *       inner block sits inside the {@code dsl.transactionResult(configuration
 *       -> ...)} lambda; we drive it by handing the lambda a real jOOQ
 *       {@code Configuration} backed by a {@link MockConnection} whose
 *       {@link MockDataProvider} succeeds for the CUSTOMER select / CUSTOMER
 *       update and throws on the PROCTRAN insert.
 * </ul>
 *
 * <p>See <a href="https://github.com/augment-solutions/cbsa-java/issues/36">#36</a>
 * for the production fix (PR #37) that ensures these jOOQ DAE catches actually
 * fire under Spring Boot's {@code DefaultExceptionTranslatorExecuteListener}.
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

    @Test
    void nonSerializationProctranInsertFailureWrapsAsHwptAbend() {
        SQLException proctranSqle = new SQLException("PROCTRAN insert failed", "23505");
        wireTransactionWithProctranFailure(proctranSqle);

        assertThatThrownBy(() -> repository.updateCustomer(
                SORTCODE, REQUEST, TRANSACTION_REFERENCE, TRANSACTION_DATE, TRANSACTION_TIME))
                .isInstanceOf(CbsaAbendException.class)
                .satisfies(thrown -> {
                    CbsaAbendException abend = (CbsaAbendException) thrown;
                    assertThat(abend.getAbendCode()).isEqualTo("HWPT");
                    assertThat(abend.getMessage())
                            .isEqualTo("UPDCUST failed to write the audit trail.");
                });
    }

    @Test
    void serializationProctranInsertFailureSurfacesAsXrtyAfterRetryExhaustion() {
        SQLException proctranSqle = new SQLException("Serialization failure", "40001");
        wireTransactionWithProctranFailure(proctranSqle);

        // The inner catch must re-throw the SQLSTATE 40001 DAE unchanged so
        // CrdbRetry sees it; after MAX_ATTEMPTS retries the outer catch
        // converts it to XRTY. We assert the terminal classification rather
        // than count attempts to keep the test resilient to retry tuning.
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

    /**
     * Drive the public {@code updateCustomer} through to the inner PROCTRAN
     * catch by:
     * <ul>
     *   <li>building a real {@code Configuration} backed by a
     *       {@link MockConnection} whose {@link MockDataProvider}:
     *       <ul>
     *         <li>returns a single CUSTOMER row for the {@code SELECT ... FOR
     *             UPDATE} so {@code fetchOne} surfaces an existing record,</li>
     *         <li>reports 1 row affected for the CUSTOMER update so the method
     *             reaches the PROCTRAN insert,</li>
     *         <li>throws {@code sqle} on the PROCTRAN insert.</li>
     *       </ul>
     *   <li>stubbing {@code dsl.transactionResult(callable)} on the outer mock
     *       to invoke the callable with that real configuration. The lambda's
     *       {@code DSL.using(configuration)} call then returns a real
     *       {@code DSLContext} that runs SQL through the provider.
     * </ul>
     * This avoids static mocking (subclass MockMaker can't do that) and avoids
     * deep-stubbing every overloaded {@code .set(...)} link.
     */
    @SuppressWarnings("unchecked")
    private void wireTransactionWithProctranFailure(SQLException proctranFailure) {
        MockDataProvider provider = ctx -> {
            String sql = ctx.sql();
            String upper = sql == null ? "" : sql.toUpperCase(Locale.ROOT);
            if (upper.contains("\"CUSTOMER\"") && upper.startsWith("SELECT")) {
                DSLContext create = DSL.using(SQLDialect.POSTGRES);
                org.jooq.Result<com.augment.cbsa.jooq.tables.records.CustomerRecord> result =
                        create.newResult(CUSTOMER);
                com.augment.cbsa.jooq.tables.records.CustomerRecord row =
                        create.newRecord(CUSTOMER);
                row.setSortcode(SORTCODE);
                row.setCustomerNumber(REQUEST.customerNumber());
                row.setName("Mr Old Name");
                row.setAddress("0 Old Street");
                row.setDateOfBirth(LocalDate.of(2000, 1, 10));
                row.setCreditScore((short) 500);
                row.setCsReviewDate(LocalDate.of(2026, 5, 8));
                result.add(row);
                return new MockResult[] { new MockResult(1, result) };
            }
            if (upper.contains("\"PROCTRAN\"")) {
                throw proctranFailure;
            }
            // CUSTOMER update reports 1 row affected.
            return new MockResult[] { new MockResult(1) };
        };
        Connection connection = new MockConnection(provider);
        // Mirror what JooqAutoConfiguration installs in production: translate
        // the underlying SQLException into a Spring DAE via
        // SQLStateSQLExceptionTranslator (which preserves SQLSTATE 40001 as
        // TransientDataAccessException). The repository catches
        // org.springframework.dao.DataAccessException, so without this listener
        // the test would surface the raw jOOQ DAE.
        SQLStateSQLExceptionTranslator translator = new SQLStateSQLExceptionTranslator();
        ExecuteListener springTranslator = new ExecuteListener() {
            @Override
            public void exception(ExecuteContext ctx) {
                SQLException sqle = ctx.sqlException();
                if (sqle != null) {
                    DataAccessException translated = translator.translate("jOOQ", ctx.sql(), sqle);
                    if (translated != null) {
                        ctx.exception(translated);
                    }
                }
            }
        };
        Configuration realConfiguration = DSL.using(connection, SQLDialect.POSTGRES)
                .configuration()
                .deriveAppending(springTranslator);

        when(dsl.transactionResult((TransactionalCallable<UpdcustResult>) any()))
                .thenAnswer(invocation -> {
                    TransactionalCallable<UpdcustResult> callable = invocation.getArgument(0);
                    return callable.run(realConfiguration);
                });
    }
}
