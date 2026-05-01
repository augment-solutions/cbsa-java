package com.augment.cbsa.repository;

import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.jooq.tables.records.CustomerRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.augment.cbsa.jooq.Tables.CUSTOMER;

@Repository
public class CustomerRepository {

    private final DSLContext dsl;

    public CustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<CustomerDetails> findBySortcodeAndCustomerNumber(String sortcode, long customerNumber) {
        return dsl.selectFrom(CUSTOMER)
                .where(CUSTOMER.SORTCODE.eq(sortcode))
                .and(CUSTOMER.CUSTOMER_NUMBER.eq(customerNumber))
                .fetchOptional(this::toDomain);
    }

    public Optional<CustomerDetails> findLastBySortcode(String sortcode) {
        return dsl.selectFrom(CUSTOMER)
                .where(CUSTOMER.SORTCODE.eq(sortcode))
                .orderBy(CUSTOMER.CUSTOMER_NUMBER.desc())
                .limit(1)
                .fetchOptional(this::toDomain);
    }

    private CustomerDetails toDomain(CustomerRecord record) {
        return new CustomerDetails(
                record.getSortcode(),
                record.getCustomerNumber(),
                record.getName(),
                record.getAddress(),
                record.getDateOfBirth(),
                record.getCreditScore() == null ? 0 : record.getCreditScore(),
                record.getCsReviewDate()
        );
    }
}