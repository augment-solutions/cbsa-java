package com.augment.cbsa.repository;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class XfrfunRepositoryTest {

    @Test
    void toTransferDescriptionFormatsCorrectly() throws Exception {
        DSLContext dsl = mock(DSLContext.class);
        XfrfunRepository repository = new XfrfunRepository(dsl);

        Method method = XfrfunRepository.class.getDeclaredMethod("toTransferDescription", String.class, long.class);
        method.setAccessible(true);

        String description = (String) method.invoke(repository, "987654", 12345678L);

        assertThat(description).isEqualTo("TRANSFER                  98765412345678");
        assertThat(description.length()).isEqualTo(40);
    }

    @Test
    void toTransferDescriptionRejectsInvalidSortcodeLength() throws Exception {
        DSLContext dsl = mock(DSLContext.class);
        XfrfunRepository repository = new XfrfunRepository(dsl);

        Method method = XfrfunRepository.class.getDeclaredMethod("toTransferDescription", String.class, long.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> method.invoke(repository, "12345", 12345678L))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toSortcode must be exactly 6 characters");
    }

    @Test
    void insertTransferAuditRejectsNullParameters() {
        DSLContext dsl = mock(DSLContext.class);
        XfrfunRepository repository = new XfrfunRepository(dsl);

        assertThatThrownBy(() -> repository.insertTransferAudit(
                null,
                12345678L,
                "987654",
                87654321L,
                1L,
                LocalDate.now(),
                LocalTime.now(),
                new BigDecimal("25.00")
        )).isInstanceOf(NullPointerException.class).hasMessage("fromSortcode must not be null");
    }

    @Test
    void constructorRejectsNullDsl() {
        assertThatThrownBy(() -> new XfrfunRepository(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dsl must not be null");
    }
}
