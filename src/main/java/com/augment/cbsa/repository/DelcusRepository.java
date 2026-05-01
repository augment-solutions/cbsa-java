package com.augment.cbsa.repository;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CustomerDetails;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.ACCOUNT;
import static com.augment.cbsa.jooq.Tables.CUSTOMER;
import static com.augment.cbsa.jooq.Tables.PROCTRAN;

@Repository
public class DelcusRepository {

    private static final DateTimeFormatter ACCOUNT_AUDIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);
    private static final DateTimeFormatter CUSTOMER_AUDIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private final DSLContext dsl;

    public DelcusRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    public int deleteAccount(String sortcode, long accountNumber) {
        return dsl.deleteFrom(ACCOUNT)
                .where(ACCOUNT.SORTCODE.eq(sortcode))
                .and(ACCOUNT.ACCOUNT_NUMBER.eq(accountNumber))
                .execute();
    }

    public void insertAccountDeletionAudit(
            AccountDetails account,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime
    ) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");

        dsl.insertInto(PROCTRAN)
                .set(PROCTRAN.SORTCODE, account.sortcode())
                .set(PROCTRAN.LOGICAL_DELETE, false)
                .set(PROCTRAN.TRAN_DATE, transactionDate)
                .set(PROCTRAN.TRAN_TIME, transactionTime)
                .set(PROCTRAN.TRAN_REF, transactionReference)
                .set(PROCTRAN.TRAN_TYPE, "ODA")
                .set(PROCTRAN.DESCRIPTION, toAccountDeletionDescription(account))
                .set(PROCTRAN.AMOUNT, account.actualBalance())
                .execute();
    }

    public int deleteCustomer(String sortcode, long customerNumber) {
        return dsl.deleteFrom(CUSTOMER)
                .where(CUSTOMER.SORTCODE.eq(sortcode))
                .and(CUSTOMER.CUSTOMER_NUMBER.eq(customerNumber))
                .execute();
    }

    public void insertCustomerDeletionAudit(
            CustomerDetails customer,
            long transactionReference,
            LocalDate transactionDate,
            LocalTime transactionTime
    ) {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");

        dsl.insertInto(PROCTRAN)
                .set(PROCTRAN.SORTCODE, customer.sortcode())
                .set(PROCTRAN.LOGICAL_DELETE, false)
                .set(PROCTRAN.TRAN_DATE, transactionDate)
                .set(PROCTRAN.TRAN_TIME, transactionTime)
                .set(PROCTRAN.TRAN_REF, transactionReference)
                .set(PROCTRAN.TRAN_TYPE, "ODC")
                .set(PROCTRAN.DESCRIPTION, toCustomerDeletionDescription(customer))
                .set(PROCTRAN.AMOUNT, BigDecimal.ZERO.setScale(2))
                .execute();
    }

    private String toAccountDeletionDescription(AccountDetails account) {
        return String.format(
                Locale.ROOT,
                "%010d%-8.8s%s%sDELETE",
                account.customerNumber(),
                account.accountType(),
                toCobolDate(account.lastStatementDate()),
                toCobolDate(account.nextStatementDate())
        );
    }

    private String toCustomerDeletionDescription(CustomerDetails customer) {
        return customer.sortcode()
                + String.format(Locale.ROOT, "%010d", customer.customerNumber())
                + String.format(Locale.ROOT, "%-14.14s", customer.name())
                + customer.dateOfBirth().format(CUSTOMER_AUDIT_DATE_FORMATTER);
    }

    private String toCobolDate(LocalDate date) {
        return date == null ? "00000000" : date.format(ACCOUNT_AUDIT_DATE_FORMATTER);
    }
}
