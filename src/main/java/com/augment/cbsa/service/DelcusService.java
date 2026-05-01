package com.augment.cbsa.service;

import com.augment.cbsa.domain.AccountDetails;
import com.augment.cbsa.domain.CustomerDetails;
import com.augment.cbsa.domain.DelcusRequest;
import com.augment.cbsa.domain.DelcusResult;
import com.augment.cbsa.domain.InqacccuRequest;
import com.augment.cbsa.domain.InqacccuResult;
import com.augment.cbsa.domain.InqcustRequest;
import com.augment.cbsa.domain.InqcustResult;
import com.augment.cbsa.error.CbsaAbendException;
import com.augment.cbsa.repository.CrdbRetry;
import com.augment.cbsa.repository.DelcusRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DelcusService {

    private static final String ACCOUNT_INQUIRY_ABEND_CODE = "WPV6";
    private static final String DELETE_ABEND_CODE = "WPV7";
    private static final String PROCTRAN_ABEND_CODE = "HWPT";

    private final InqcustService inqcustService;
    private final InqacccuService inqacccuService;
    private final DelcusRepository delcusRepository;
    private final DSLContext dsl;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DelcusService(
            InqcustService inqcustService,
            InqacccuService inqacccuService,
            DelcusRepository delcusRepository,
            DSLContext dsl,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.inqcustService = Objects.requireNonNull(inqcustService, "inqcustService must not be null");
        this.inqacccuService = Objects.requireNonNull(inqacccuService, "inqacccuService must not be null");
        this.delcusRepository = Objects.requireNonNull(delcusRepository, "delcusRepository must not be null");
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DelcusResult delete(DelcusRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return CrdbRetry.run(dsl, () -> transactionTemplate.execute(status -> deleteWithinTransaction(request)));
    }

    private DelcusResult deleteWithinTransaction(DelcusRequest request) {
        InqcustResult customerResult = inqcustService.inquire(new InqcustRequest(request.customerNumber()));
        if (!customerResult.inquirySuccess()) {
            return DelcusResult.failure(customerResult.failCode(), customerResult.customerNumber(), customerResult.message());
        }

        CustomerDetails customer = Objects.requireNonNull(customerResult.customer(), "Successful inquiry requires a customer");
        InqacccuResult accountsResult = inqacccuService.inquire(new InqacccuRequest(customer.customerNumber()));
        List<AccountDetails> accounts = accountsToDelete(customer, accountsResult);

        Instant now = Instant.now(clock);
        long transactionReference = Math.max(0L, now.toEpochMilli());
        LocalDate transactionDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalTime transactionTime = LocalTime.ofInstant(now, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

        for (AccountDetails account : accounts) {
            deleteAccount(account, transactionReference, transactionDate, transactionTime);
        }

        deleteCustomer(customer, transactionReference, transactionDate, transactionTime);
        return DelcusResult.success(customer);
    }

    private List<AccountDetails> accountsToDelete(CustomerDetails customer, InqacccuResult accountsResult) {
        if (accountsResult.inquirySuccess()) {
            return accountsResult.accounts();
        }
        if (accountsResult.isNotFoundFailure()) {
            return List.of();
        }

        throw new CbsaAbendException(
                ACCOUNT_INQUIRY_ABEND_CODE,
                "DELCUS failed to read accounts for customer %d.".formatted(customer.customerNumber())
        );
    }

    private void deleteAccount(AccountDetails account, long transactionReference, LocalDate transactionDate, LocalTime transactionTime) {
        int deletedRows;
        try {
            deletedRows = delcusRepository.deleteAccount(account.sortcode(), account.accountNumber());
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(
                    DELETE_ABEND_CODE,
                    "DELCUS failed to delete account %d.".formatted(account.accountNumber()),
                    exception
            );
        }

        if (deletedRows == 0) {
            return;
        }
        if (deletedRows != 1) {
            throw new CbsaAbendException(
                    DELETE_ABEND_CODE,
                    "DELCUS deleted an unexpected number of rows for account %d.".formatted(account.accountNumber())
            );
        }

        try {
            delcusRepository.insertAccountDeletionAudit(account, transactionReference, transactionDate, transactionTime);
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(
                    PROCTRAN_ABEND_CODE,
                    "DELCUS failed to write the account deletion audit trail.",
                    exception
            );
        }
    }

    private void deleteCustomer(CustomerDetails customer, long transactionReference, LocalDate transactionDate, LocalTime transactionTime) {
        int deletedRows;
        try {
            deletedRows = delcusRepository.deleteCustomer(customer.sortcode(), customer.customerNumber());
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(DELETE_ABEND_CODE, "DELCUS failed to delete the customer data.", exception);
        }

        if (deletedRows == 0) {
            return;
        }
        if (deletedRows != 1) {
            throw new CbsaAbendException(
                    DELETE_ABEND_CODE,
                    "DELCUS deleted an unexpected number of rows for customer %d.".formatted(customer.customerNumber())
            );
        }

        try {
            delcusRepository.insertCustomerDeletionAudit(customer, transactionReference, transactionDate, transactionTime);
        } catch (DataAccessException exception) {
            throw new CbsaAbendException(
                    PROCTRAN_ABEND_CODE,
                    "DELCUS failed to write the customer deletion audit trail.",
                    exception
            );
        }
    }
}
