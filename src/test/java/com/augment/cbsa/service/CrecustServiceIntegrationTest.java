package com.augment.cbsa.service;

import com.augment.cbsa.domain.CrecustRequest;
import com.augment.cbsa.domain.CrecustResult;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.time.LocalDate;
import java.util.Random;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CONTROL;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class CrecustServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private CrecustService crecustService;

    @MockBean(name = "crecustReviewRandom")
    private Random reviewDateRandom;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(CUSTOMER).execute();
        dsl.update(CONTROL)
                .set(CONTROL.CUSTOMER_COUNT, 0L)
                .set(CONTROL.CUSTOMER_LAST, 0L)
                .set(CONTROL.ACCOUNT_COUNT, 0L)
                .set(CONTROL.ACCOUNT_LAST, 0L)
                .where(CONTROL.ID.eq("GLOBAL"))
                .execute();
    }

    @Test
    void createsCustomerAuditTrailAndControlRow() {
        when(reviewDateRandom.nextInt(20)).thenReturn(3);

        LocalDate today = LocalDate.now(java.time.Clock.systemUTC());
        CrecustResult result = crecustService.create(new CrecustRequest(
                "Dr Alice Example",
                "1 Main Street",
                10_01_2000
        ));

        assertThat(result.creationSuccess()).isTrue();
        assertThat(result.customer()).isNotNull();
        assertThat(result.customer().customerNumber()).isEqualTo(1L);
        assertThat(result.customer().sortcode()).isEqualTo("987654");
        assertThat(result.customer().csReviewDate()).isEqualTo(today.plusDays(4));

        assertThat(dsl.fetchCount(CUSTOMER)).isEqualTo(1);
        assertThat(dsl.fetchCount(PROCTRAN)).isEqualTo(1);
        assertThat(dsl.select(CONTROL.CUSTOMER_COUNT, CONTROL.CUSTOMER_LAST)
                .from(CONTROL)
                .where(CONTROL.ID.eq("GLOBAL"))
                .fetchOne())
                .extracting(r -> r.get(CONTROL.CUSTOMER_COUNT), r -> r.get(CONTROL.CUSTOMER_LAST))
                .containsExactly(1L, 1L);
        assertThat(dsl.select(PROCTRAN.DESCRIPTION)
                .from(PROCTRAN)
                .fetchSingle(PROCTRAN.DESCRIPTION))
                .startsWith("9876540000000001Dr Alice Examp10/01/2000");
    }

    @Test
    void doesNotPersistAnythingForInvalidTitles() {
        CrecustResult result = crecustService.create(new CrecustRequest("Alice Example", "1 Main Street", 10_01_2000));

        assertThat(result.creationSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("T");
        assertThat(dsl.fetchCount(CUSTOMER)).isZero();
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
        assertThat(dsl.select(CONTROL.CUSTOMER_COUNT, CONTROL.CUSTOMER_LAST)
                .from(CONTROL)
                .where(CONTROL.ID.eq("GLOBAL"))
                .fetchOne())
                .extracting(r -> r.get(CONTROL.CUSTOMER_COUNT), r -> r.get(CONTROL.CUSTOMER_LAST))
                .containsExactly(0L, 0L);
    }
}
