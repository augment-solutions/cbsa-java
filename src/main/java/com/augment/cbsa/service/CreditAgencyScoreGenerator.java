package com.augment.cbsa.service;

import com.augment.cbsa.domain.CreditAgency;
import com.augment.cbsa.domain.CrecustRequest;

@FunctionalInterface
public interface CreditAgencyScoreGenerator {

    int nextCreditScore(CreditAgency agency, CrecustRequest request);
}