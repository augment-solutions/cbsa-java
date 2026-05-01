package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.support.AbstractCockroachIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DelcusRepositoryTest extends AbstractCockroachIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private DelcusRepository delcusRepository;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(PROCTRAN).execute();
        dsl.deleteFrom(ACCOUNT).execute();
        dsl.deleteFrom(CUSTOMER).execute();
    }

    @Test
    void deleteAccountRemovesTheMatchingRow() {
        insertAccount(10L, 100L, "ISA", new BigDecimal("1500.00"));

        int deletedRows = delcusRepository.deleteAccount("987654", 100L);

        assertThat(deletedRows).isEqualTo(1);
        assertThat(dsl.fetchCount(ACCOUNT)).isZero();
    }

    @Test
    void deleteAccountReturnsZeroWhenNoMatchingRow() {
        int deletedRows = delcusRepository.deleteAccount("987654", 999L);

        assertThat(deletedRows).isZero();
    }

    @Test
    void insertAccountDeletionAuditWritesCorrectlyFormattedDescription() {
        AccountDetails account = new AccountDetails(
                "987654",
                10L,
                100L,
                "ISA",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("1499.75"),
                new BigDecimal("1499.75")
        );

        delcusRepository.insertAccountDeletionAudit(
                account,
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30)
        );

        Record auditRow = dsl.select(PROCTRAN.asterisk())
                .from(PROCTRAN)
                .fetchSingle();

        assertThat(auditRow.get(PROCTRAN.SORTCODE)).isEqualTo("987654");
        assertThat(auditRow.get(PROCTRAN.TRAN_TYPE)).isEqualTo("ODA");
        assertThat(auditRow.get(PROCTRAN.DESCRIPTION)).isEqualTo("0000000010ISA     0302202404032024DELETE");
        assertThat(auditRow.get(PROCTRAN.AMOUNT)).isEqualTo(new BigDecimal("1499.75"));
        assertThat(auditRow.get(PROCTRAN.TRAN_REF)).isEqualTo(1234567890L);
        assertThat(auditRow.get(PROCTRAN.TRAN_DATE)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(auditRow.get(PROCTRAN.TRAN_TIME)).isEqualTo(LocalTime.of(10, 15, 30));
        assertThat(auditRow.get(PROCTRAN.LOGICAL_DELETE)).isFalse();
    }

    @Test
    void insertAccountDeletionAuditHandlesNullDatesCorrectly() {
        AccountDetails account = new AccountDetails(
                "987654",
                10L,
                100L,
                "CURRENT",
                new BigDecimal("0.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("0.00"),
                null,
                null,
                new BigDecimal("500.00"),
                new BigDecimal("500.00")
        );

        delcusRepository.insertAccountDeletionAudit(
                account,
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30)
        );

        String description = dsl.select(PROCTRAN.DESCRIPTION)
                .from(PROCTRAN)
                .fetchSingle(PROCTRAN.DESCRIPTION);

        assertThat(description).isEqualTo("0000000010CURRENT 0000000000000000DELETE");
    }

    @Test
    void insertAccountDeletionAuditTruncatesLongAccountTypes() {
        AccountDetails account = new AccountDetails(
                "987654",
                10L,
                100L,
                "VERYLONGACCOUNTTYPE",
                new BigDecimal("1.50"),
                LocalDate.of(2024, 1, 2),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 2, 3),
                LocalDate.of(2024, 3, 4),
                new BigDecimal("100.00"),
                new BigDecimal("100.00")
        );

        delcusRepository.insertAccountDeletionAudit(
                account,
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30)
        );

        String description = dsl.select(PROCTRAN.DESCRIPTION)
                .from(PROCTRAN)
                .fetchSingle(PROCTRAN.DESCRIPTION);

        // Should truncate to 8 characters
        assertThat(description).startsWith("0000000010VERYLONG");
    }

    @Test
    void deleteCustomerRemovesTheMatchingRow() {
        insertCustomer(10L, "Mr Alice Example");

        int deletedRows = delcusRepository.deleteCustomer("987654", 10L);

        assertThat(deletedRows).isEqualTo(1);
        assertThat(dsl.fetchCount(CUSTOMER)).isZero();
    }

    @Test
    void deleteCustomerReturnsZeroWhenNoMatchingRow() {
        int deletedRows = delcusRepository.deleteCustomer("987654", 999L);

        assertThat(deletedRows).isZero();
    }

    @Test
    void insertCustomerDeletionAuditWritesCorrectlyFormattedDescription() {
        CustomerDetails customer = new CustomerDetails(
                "987654",
                10L,
                "Mr Alice Example",
                "1 Main Street",
                LocalDate.of(2000, 1, 10),
                430,
                LocalDate.of(2026, 5, 8)
        );

        delcusRepository.insertCustomerDeletionAudit(
                customer,
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30)
        );

        Record auditRow = dsl.select(PROCTRAN.asterisk())
                .from(PROCTRAN)
                .fetchSingle();

        assertThat(auditRow.get(PROCTRAN.SORTCODE)).isEqualTo("987654");
        assertThat(auditRow.get(PROCTRAN.TRAN_TYPE)).isEqualTo("ODC");
        assertThat(auditRow.get(PROCTRAN.DESCRIPTION)).isEqualTo("9876540000000010Mr Alice Examp10/01/2000");
        assertThat(auditRow.get(PROCTRAN.AMOUNT)).isEqualTo(new BigDecimal("0.00"));
        assertThat(auditRow.get(PROCTRAN.TRAN_REF)).isEqualTo(1234567890L);
        assertThat(auditRow.get(PROCTRAN.TRAN_DATE)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(auditRow.get(PROCTRAN.TRAN_TIME)).isEqualTo(LocalTime.of(10, 15, 30));
        assertThat(auditRow.get(PROCTRAN.LOGICAL_DELETE)).isFalse();
    }

    @Test
    void insertCustomerDeletionAuditTruncatesLongNames() {
        CustomerDetails customer = new CustomerDetails(
                "987654",
                10L,
                "Dr Christopher Alexander Montgomery-Smithson III",
                "1 Main Street",
                LocalDate.of(2000, 1, 10),
                430,
                LocalDate.of(2026, 5, 8)
        );

        delcusRepository.insertCustomerDeletionAudit(
                customer,
                1234567890L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15, 30)
        );

        String description = dsl.select(PROCTRAN.DESCRIPTION)
                .from(PROCTRAN)
                .fetchSingle(PROCTRAN.DESCRIPTION);

        // Name should be truncated to 14 characters
        assertThat(description).isEqualTo("9876540000000010Dr Christophe10/01/2000");
    }

    private void insertCustomer(long customerNumber, String name) {
        dsl.insertInto(CUSTOMER)
                .set(CUSTOMER.SORTCODE, "987654")
                .set(CUSTOMER.CUSTOMER_NUMBER, customerNumber)
                .set(CUSTOMER.NAME, name)
                .set(CUSTOMER.ADDRESS, "1 Main Street")
                .set(CUSTOMER.DATE_OF_BIRTH, LocalDate.of(2000, 1, 10))
                .set(CUSTOMER.CREDIT_SCORE, (short) 430)
                .set(CUSTOMER.CS_REVIEW_DATE, LocalDate.of(2026, 5, 8))
                .execute();
    }

    private void insertAccount(long customerNumber, long accountNumber, String accountType, BigDecimal actualBalance) {
        dsl.insertInto(ACCOUNT)
                .set(ACCOUNT.SORTCODE, "987654")
                .set(ACCOUNT.ACCOUNT_NUMBER, accountNumber)
                .set(ACCOUNT.CUSTOMER_NUMBER, customerNumber)
                .set(ACCOUNT.ACCOUNT_TYPE, accountType)
                .set(ACCOUNT.INTEREST_RATE, new BigDecimal("1.50"))
                .set(ACCOUNT.OPENED, LocalDate.of(2024, 1, 2))
                .set(ACCOUNT.OVERDRAFT_LIMIT, new BigDecimal("250.00"))
                .set(ACCOUNT.LAST_STMT_DATE, LocalDate.of(2024, 2, 3))
                .set(ACCOUNT.NEXT_STMT_DATE, LocalDate.of(2024, 3, 4))
                .set(ACCOUNT.AVAILABLE_BALANCE, actualBalance)
                .set(ACCOUNT.ACTUAL_BALANCE, actualBalance)
                .execute();
    }
}
