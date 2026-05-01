package com.augment.cbsa.service;

import com.augment.cbsa.domain.CreditAgency;
import java.time.Duration;

@FunctionalInterface
public interface CreditAgencyDelayGenerator {

    Duration nextDelay(CreditAgency agency);
}