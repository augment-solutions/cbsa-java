package com.augment.cbsa.service;

import com.augment.cbsa.domain.UpdcustRequest;
import com.augment.cbsa.domain.UpdcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CONTROL;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UpdcustServiceIntegrationTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UpdcustService updcustService;

    @Autowired
    private Clock clock;

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
    void updatesCustomerAndWritesAuditRow() {
        insertCustomer(1L, "Mr Old Name", "9 Old Street", LocalDate.of(2000, 1, 10), (short) 430, LocalDate.of(2026, 5, 8));
        LocalDate today = LocalDate.now(clock);

        UpdcustResult result = updcustService.update(new UpdcustRequest(
                1L,
                "Mrs Alice Example",
                "1 Main Street",
                10_01_2000,
                999,
                99_99_9999
        ));

        assertThat(result.updateSuccess()).isTrue();
        assertThat(result.customer()).isNotNull();
        assertThat(result.customer().name()).isEqualTo("Mrs Alice Example");
        assertThat(result.customer().address()).isEqualTo("1 Main Street");
        assertThat(result.customer().dateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 10));
        assertThat(result.customer().creditScore()).isEqualTo(430);
        assertThat(dsl.fetchCount(PROCTRAN)).isEqualTo(1);
        assertThat(dsl.select(PROCTRAN.TRAN_TYPE).from(PROCTRAN).fetchSingle(PROCTRAN.TRAN_TYPE)).isEqualTo("OUC");
        assertThat(dsl.select(PROCTRAN.TRAN_DATE).from(PROCTRAN).fetchSingle(PROCTRAN.TRAN_DATE)).isEqualTo(today);
        assertThat(dsl.select(PROCTRAN.DESCRIPTION).from(PROCTRAN).fetchSingle(PROCTRAN.DESCRIPTION))
                .isEqualTo(String.format(Locale.ROOT, "%s%010d%-14.14s%s", "987654", 1L, "Mrs Alice Example", "10/01/2000"));
        assertThat(dsl.select(CUSTOMER.NAME, CUSTOMER.ADDRESS)
                .from(CUSTOMER)
                .where(CUSTOMER.SORTCODE.eq("987654"))
                .and(CUSTOMER.CUSTOMER_NUMBER.eq(1L))
                .fetchOne())
                .extracting(r -> r.get(CUSTOMER.NAME), r -> r.get(CUSTOMER.ADDRESS))
                .containsExactly("Mrs Alice Example", "1 Main Street");
    }

    @Test
    void returnsNotFoundWithoutWritingAuditRow() {
        UpdcustResult result = updcustService.update(new UpdcustRequest(9L, "Mrs Alice Example", "1 Main Street", 10_01_2000, 430, 8_052_026));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("1");
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
    }

    @Test
    void rejectsWhenNameAndAddressAreBothBlankish() {
        insertCustomer(1L, "Mr Old Name", "9 Old Street", LocalDate.of(2000, 1, 10), (short) 430, LocalDate.of(2026, 5, 8));

        UpdcustResult result = updcustService.update(new UpdcustRequest(1L, " ", " ", 10_01_2000, 430, 8_052_026));

        assertThat(result.updateSuccess()).isFalse();
        assertThat(result.failCode()).isEqualTo("4");
        assertThat(dsl.fetchCount(PROCTRAN)).isZero();
        assertThat(dsl.select(CUSTOMER.NAME).from(CUSTOMER).fetchSingle(CUSTOMER.NAME)).isEqualTo("Mr Old Name");
    }

    @Test
    void proctranInsertFailureSurfacesHwptAbend() {
        // Force every PROCTRAN insert to fail at the database layer so the
        // inner catch in UpdcustRepository.updateCustomer translates the
        // failure into the HWPT system abend. Without #36's fix the catch
        // never matches and the test sees a Spring DataIntegrityViolation
        // bubble out instead, classified as UNEX.
        insertCustomer(1L, "Mr Old Name", "9 Old Street", LocalDate.of(2000, 1, 10), (short) 430, LocalDate.of(2026, 5, 8));
        // The Cockroach container is a singleton shared across every
        // integration test class, so leaking 'proctran_block_inserts' would
        // poison every later PROCTRAN write. Drop any leftover constraint
        // from a previously-killed JVM before we add ours, run the ADD
        // inside the try so cleanup still fires if the assertion or service
        // call throws, and use DROP ... IF EXISTS in the finally for the
        // same reason.
        dsl.execute("ALTER TABLE proctran DROP CONSTRAINT IF EXISTS proctran_block_inserts");
        try {
            dsl.execute("ALTER TABLE proctran ADD CONSTRAINT proctran_block_inserts CHECK (false) NOT VALID");
            assertThatThrownBy(() -> updcustService.update(new UpdcustRequest(
                    1L,
                    "Mrs Alice Example",
                    "1 Main Street",
                    10_01_2000,
                    999,
                    99_99_9999
            )))
                    .isInstanceOf(CbsaAbendException.class)
                    .satisfies(thrown -> {
                        CbsaAbendException abend = (CbsaAbendException) thrown;
                        assertThat(abend.getAbendCode()).isEqualTo("HWPT");
                    });
        } finally {
            dsl.execute("ALTER TABLE proctran DROP CONSTRAINT IF EXISTS proctran_block_inserts");
        }
    }

    private void insertCustomer(long customerNumber, String name, String address, LocalDate dateOfBirth, short creditScore, LocalDate csReviewDate) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, name)
                .set(CUSTOMER.ADDRESS, address)
                .set(CUSTOMER.DATE_OF_BIRTH, dateOfBirth)
                .set(CUSTOMER.CREDIT_SCORE, creditScore)
                .set(CUSTOMER.CS_REVIEW_DATE, csReviewDate)
                .execute();
    }
}
