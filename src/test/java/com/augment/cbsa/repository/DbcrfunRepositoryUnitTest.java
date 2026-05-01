package com.augment.cbsa.repository;

import com.augment.cbsa.error.CbsaAbendException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbcrfunRepositoryUnitTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private DbcrfunRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DbcrfunRepository(dsl);
    }

    @Test
    void nonSerializationProctranInsertFailureWrapsAsHwptAbend() {
        when(dsl.insertInto(PROCTRAN)).thenThrow(new DataAccessException("PROCTRAN insert failed") { });

        assertThatThrownBy(() -> repository.insertProcTran(
                "987654",
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30),
                "TFR",
                "TRANSFER",
                new BigDecimal("25.00")
        ))
                .isInstanceOf(CbsaAbendException.class)
                .satisfies(thrown -> {
                    CbsaAbendException abend = (CbsaAbendException) thrown;
                    assertThat(abend.getAbendCode()).isEqualTo("HWPT");
                    assertThat(abend.getMessage()).isEqualTo("DBCRFUN failed to write the audit trail.");
                });
    }

    @Test
    void serializationProctranInsertFailureIsRethrownForCrdbRetry() {
        SQLException sqle = new SQLException("Serialization failure", "40001");
        DataAccessException dae = new DataAccessException("wrapped", sqle) { };
        when(dsl.insertInto(PROCTRAN)).thenThrow(dae);

        assertThatThrownBy(() -> repository.insertProcTran(
                "987654",
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30),
                "TFR",
                "TRANSFER",
                new BigDecimal("25.00")
        ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("wrapped");
    }
}
