package com.augment.cbsa.service;

import com.augment.cbsa.domain.UpdaccRequest;
import com.augment.cbsa.domain.UpdaccResult;
import com.augment.cbsa.repository.UpdaccRepository;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UpdaccService {

    private final UpdaccRepository updaccRepository;
    private final String sortcode;

    public UpdaccService(UpdaccRepository updaccRepository, @Value("${cbsa.sortcode}") String sortcode) {
        this.updaccRepository = Objects.requireNonNull(updaccRepository, "updaccRepository must not be null");
        this.sortcode = Objects.requireNonNull(sortcode, "sortcode must not be null");
    }

    public UpdaccResult update(UpdaccRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (isInvalidAccountType(request.accountType())) {
            return UpdaccResult.failure("2", "Account type must not be blank or start with a space.");
        }

        return updaccRepository.updateAccount(sortcode, request);
    }

    private boolean isInvalidAccountType(String accountType) {
        return accountType.isBlank() || accountType.charAt(0) == ' ';
    }
}